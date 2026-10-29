package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.facturacion.FacturacionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.empresa.api.facturacion.FacturacionDtos.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class FacturacionConcurrenteTests extends PostgreSqlIntegrationTest {
    @Autowired FacturacionService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource source;
    @Autowired Clock clock;
    int admin,serie;
    LocalDate hoy;
    @BeforeEach void preparar() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("dorna_test");
        hoy=LocalDate.now(clock);
        admin=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,'inutilizable') RETURNING id_usuario",Integer.class,"test-fc-"+UUID.randomUUID());
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",admin);
        serie=service.crearSerie(admin,1,Tipo.CLIENTE,new NuevaSerie("Concurrente "+admin,"T"+admin+"-",hoy.getYear(),6,false)).id();
    }
    Detalle crear() { return service.crear(admin,1,Tipo.CLIENTE,new Nuevo(serie,101,null,hoy,null,null,List.of(new EntradaLinea("Prueba","2","10","0","21")))); }
    @AfterEach void limpiar() {
        var facturas=jdbc.queryForList("SELECT factura_cliente FROM API_Facturacion WHERE autor=? AND factura_cliente IS NOT NULL",Integer.class,admin);
        var series=jdbc.queryForList("SELECT serie FROM API_Facturacion_Auditoria WHERE autor=? AND serie IS NOT NULL",Integer.class,admin);
        jdbc.update("DELETE FROM API_Facturacion_Auditoria WHERE autor=?",admin);
        jdbc.update("DELETE FROM API_Facturacion_Lineas WHERE documento IN (SELECT id FROM API_Facturacion WHERE autor=?)",admin);
        jdbc.update("DELETE FROM API_Facturacion WHERE autor=?",admin);
        for(int f:facturas) {
            jdbc.update("DELETE FROM Lineas_Factura_Cliente WHERE id_factura=?",f);
            jdbc.update("DELETE FROM API_Factura_Cliente_Empresa WHERE id_factura=?",f);
            jdbc.update("DELETE FROM Facturas_Cliente WHERE id_factura=?",f);
        }
        for(int s:series) jdbc.update("DELETE FROM Series_Documentos WHERE id_serie=?",s);
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",admin);
        jdbc.update("DELETE FROM Usuarios WHERE id_usuario=?",admin);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void emisionesSimultaneasConservanUnicidadYContador(boolean mismoDocumento) throws Exception {
        var first=crear(); var second=mismoDocumento?first:crear();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1); var ready=new CountDownLatch(2);
            var tasks=new ArrayList<Future<Detalle>>();
            for(int id:new int[]{first.id(),second.id()}) tasks.add(executor.submit(()->{ ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException(); return service.emitir(admin,1,Tipo.CLIENTE,id); }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            var a=tasks.get(0).get(15,TimeUnit.SECONDS); var b=tasks.get(1).get(15,TimeUnit.SECONDS);
            if(mismoDocumento) assertThat(a).isEqualTo(b); else assertThat(a.facturaId()).isNotEqualTo(b.facturaId());
            assertThat(jdbc.queryForObject("SELECT siguiente_numero FROM Series_Documentos WHERE id_serie=?",Integer.class,serie)).isEqualTo(mismoDocumento?2:3);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Facturacion_Auditoria WHERE autor=? AND accion='EMITIR'",Integer.class,admin)).isEqualTo(mismoDocumento?1:2);
        }
    }
    @Test void falloDeAuditoriaRevierteFacturaContadorYEstado() {
        var d=crear(); int antes=jdbc.queryForObject("SELECT count(*) FROM Facturas_Cliente",Integer.class);
        jdbc.update("INSERT INTO API_Facturacion_Auditoria(documento,autor,accion,fecha) VALUES (?,?,'EMITIR',CURRENT_TIMESTAMP)",d.id(),admin);
        assertThatThrownBy(()->service.emitir(admin,1,Tipo.CLIENTE,d.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(service.consultar(admin,1,Tipo.CLIENTE,d.id()).estado()).isEqualTo("BORRADOR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Facturas_Cliente",Integer.class)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT siguiente_numero FROM Series_Documentos WHERE id_serie=?",Integer.class,serie)).isEqualTo(1);
        jdbc.update("DELETE FROM API_Facturacion_Auditoria WHERE documento=? AND accion='EMITIR'",d.id());
        assertThat(service.emitir(admin,1,Tipo.CLIENTE,d.id()).numero()).endsWith("000001");
    }
    @Test void revocacionDuranteEsperaImpideEmision() throws Exception {
        var d=crear();
        try(var executor=Executors.newSingleThreadExecutor();var connection=source.getConnection()) {
            connection.setAutoCommit(false); Future<Integer> result=null;
            try {
                try(var lock=connection.prepareStatement("SELECT pg_advisory_xact_lock(1146049102,4)")) { lock.execute(); }
                result=executor.submit(()->{try { service.emitir(admin,1,Tipo.CLIENTE,d.id()); return 200; } catch(ApiException e) { return e.status(); }});
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10); boolean waiting=false;
                while(System.nanoTime()<deadline) {
                    waiting=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND classid=1146049102 AND objid=4 AND NOT granted AND database=(SELECT oid FROM pg_database WHERE datname=current_database()))",Boolean.class);
                    if(waiting) break; Thread.sleep(20);
                }
                assertThat(waiting).isTrue();
                try(var revoke=connection.prepareStatement("DELETE FROM Usuario_Rol WHERE id_usuario=?")) { revoke.setInt(1,admin); revoke.executeUpdate(); }
                connection.commit(); assertThat(result.get(15,TimeUnit.SECONDS)).isEqualTo(404);
                assertThat(jdbc.queryForObject("SELECT estado FROM API_Facturacion WHERE id=?",String.class,d.id())).isEqualTo("BORRADOR");
            } finally { connection.rollback(); if(result!=null) result.get(15,TimeUnit.SECONDS); }
        }
    }
    @Test void rectificacionesSimultaneasSoloCreanUnBorrador() throws Exception {
        var d=crear(); service.emitir(admin,1,Tipo.CLIENTE,d.id());
        int rect=service.crearSerie(admin,1,Tipo.CLIENTE,new NuevaSerie("Rectificativa "+admin,"R"+admin+"-",hoy.getYear(),6,true)).id();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1); var ready=new CountDownLatch(2); var results=new ArrayList<Future<Integer>>();
            for(int n=0;n<2;n++) results.add(executor.submit(()->{ ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException();
                try { service.rectificar(admin,1,Tipo.CLIENTE,d.id(),new Rectificar(rect,hoy,null,"Correccion")); return 201; } catch(ApiException e) { return e.status(); } }));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(results.get(0).get(15,TimeUnit.SECONDS),results.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
    }
}
