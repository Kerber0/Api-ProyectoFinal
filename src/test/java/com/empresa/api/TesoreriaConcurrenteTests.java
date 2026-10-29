package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.facturacion.FacturacionService;
import com.empresa.api.tesoreria.TesoreriaService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.empresa.api.tesoreria.TesoreriaRepository;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.empresa.api.facturacion.FacturacionDtos.*;
import static com.empresa.api.tesoreria.TesoreriaDtos.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class TesoreriaConcurrenteTests extends PostgreSqlIntegrationTest {
    @Autowired TesoreriaService service;
    @Autowired FacturacionService facturacion;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource source;
    @Autowired Clock clock;
    @MockitoSpyBean TesoreriaRepository repository;
    int admin,serie,documento,factura,vencimiento; LocalDate hoy;
    @BeforeEach void preparar() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("dorna_test");
        hoy=LocalDate.now(clock);
        admin=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,'inutilizable') RETURNING id_usuario",Integer.class,"test-tc-"+UUID.randomUUID());
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",admin);
        serie=facturacion.crearSerie(admin,1,Tipo.CLIENTE,new NuevaSerie("Concurrente "+admin,"TC"+admin+"-",hoy.getYear(),6,false)).id();
        var d=facturacion.crear(admin,1,Tipo.CLIENTE,new Nuevo(serie,101,null,hoy,null,null,List.of(new EntradaLinea("Prueba","1","100","0","0"))));
        documento=d.id(); factura=facturacion.emitir(admin,1,Tipo.CLIENTE,documento).facturaId();
        vencimiento=service.planificar(admin,1,Tipo.CLIENTE,documento,new Plan(List.of(new Cuota(hoy.plusDays(30),"100")))).getFirst().id();
    }
    NuevoMovimiento entrada() { return new NuevoMovimiento(UUID.randomUUID().toString(),hoy,"75","TRANSFERENCIA",List.of(new Reparto(vencimiento,"75"))); }
    @AfterEach void limpiar() {
        jdbc.update("DELETE FROM API_Tesoreria_Auditoria WHERE autor=?",admin);
        jdbc.update("DELETE FROM API_Tesoreria_Aplicaciones WHERE movimiento IN (SELECT id FROM API_Tesoreria WHERE autor=?)",admin);
        jdbc.update("DELETE FROM API_Tesoreria WHERE autor=?",admin);
        jdbc.update("DELETE FROM API_Vencimientos WHERE documento=?",documento);
        jdbc.update("DELETE FROM Cobros WHERE id_factura=?",factura);
        jdbc.update("DELETE FROM API_Facturacion_Auditoria WHERE autor=?",admin);
        jdbc.update("DELETE FROM API_Facturacion_Lineas WHERE documento=?",documento);
        jdbc.update("DELETE FROM API_Facturacion WHERE id=?",documento);
        jdbc.update("DELETE FROM Lineas_Factura_Cliente WHERE id_factura=?",factura);
        jdbc.update("DELETE FROM API_Factura_Cliente_Empresa WHERE id_factura=?",factura);
        jdbc.update("DELETE FROM Facturas_Cliente WHERE id_factura=?",factura);
        jdbc.update("DELETE FROM Series_Documentos WHERE id_serie=?",serie);
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",admin);
        jdbc.update("DELETE FROM Usuarios WHERE id_usuario=?",admin);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void cobrosSimultaneosNoDuplicanNiExcedenSaldo(boolean mismaClave) throws Exception {
        var a=entrada(); var b=mismaClave?a:entrada();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1); var ready=new CountDownLatch(2); var tasks=new ArrayList<Future<Integer>>();
            for(var req:List.of(a,b)) tasks.add(executor.submit(()->{ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException(); try {service.registrar(admin,1,Tipo.CLIENTE,req); return 201;} catch(ApiException e) {return e.status();}}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            var results=List.of(tasks.get(0).get(15,TimeUnit.SECONDS),tasks.get(1).get(15,TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrderElementsOf(mismaClave?List.of(201,201):List.of(201,409));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM Cobros WHERE id_factura=?",Integer.class,factura)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Tesoreria_Auditoria WHERE autor=? AND accion='REGISTRAR'",Integer.class,admin)).isEqualTo(1);
            assertThat(service.plan(admin,1,Tipo.CLIENTE,documento).getFirst().pendiente()).isEqualTo("25.00");
        }
    }
    @Test void reversionConcurrenteSoloGeneraUnContramovimiento() throws Exception {
        var m=service.registrar(admin,1,Tipo.CLIENTE,entrada());
        try(var executor=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1); var ready=new CountDownLatch(2); var tasks=new ArrayList<Future<Movimiento>>();
            for(int n=0;n<2;n++) tasks.add(executor.submit(()->{ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException(); return service.revertir(admin,1,Tipo.CLIENTE,m.id(),new Revertir(hoy,"Error"));}));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(tasks.get(0).get(15,TimeUnit.SECONDS)).isEqualTo(tasks.get(1).get(15,TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM Cobros WHERE id_factura=?",Integer.class,factura)).isEqualTo(2);
            assertThat(service.plan(admin,1,Tipo.CLIENTE,documento).getFirst().pendiente()).isEqualTo("100.00");
        }
    }
    @Test void falloAuditoriaDeAltaRevierteMovimientoAplicacionesYCobros() {
        var req=entrada();
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("Fallo de auditoria simulado"))
                .when(repository).auditar(org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.eq(admin),org.mockito.ArgumentMatchers.eq("REGISTRAR"),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.isNull());
        try {
            assertThatThrownBy(()->service.registrar(admin,1,Tipo.CLIENTE,req)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Tesoreria WHERE autor=?",Integer.class,admin)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Tesoreria_Aplicaciones WHERE vencimiento=?",Integer.class,vencimiento)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM Cobros WHERE id_factura=?",Integer.class,factura)).isZero();
        } finally { org.mockito.Mockito.reset(repository); }
        assertThat(service.registrar(admin,1,Tipo.CLIENTE,req).estado()).isEqualTo("REGISTRADO");
    }
    @Test void falloAuditoriaRevierteContramovimientoEstadoYAplicacion() {
        var m=service.registrar(admin,1,Tipo.CLIENTE,entrada());
        jdbc.update("INSERT INTO API_Tesoreria_Auditoria(movimiento,autor,accion,fecha) VALUES (?,?,'REVERTIR',CURRENT_TIMESTAMP)",m.id(),admin);
        assertThatThrownBy(()->service.revertir(admin,1,Tipo.CLIENTE,m.id(),new Revertir(hoy,"Error"))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(service.consultar(admin,1,Tipo.CLIENTE,m.id())).isEqualTo(m);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Cobros WHERE id_factura=?",Integer.class,factura)).isEqualTo(1);
        assertThat(service.plan(admin,1,Tipo.CLIENTE,documento).getFirst().pendiente()).isEqualTo("25.00");
        jdbc.update("DELETE FROM API_Tesoreria_Auditoria WHERE movimiento=? AND accion='REVERTIR'",m.id());
        assertThat(service.revertir(admin,1,Tipo.CLIENTE,m.id(),new Revertir(hoy,"Error")).estado()).isEqualTo("REVERTIDO");
    }
    @Test void revocacionDuranteEsperaImpideCobro() throws Exception {
        var req=entrada();
        try(var executor=Executors.newSingleThreadExecutor();var connection=source.getConnection()) {
            connection.setAutoCommit(false); Future<Integer> result=null;
            try {
                try(var lock=connection.prepareStatement("SELECT pg_advisory_xact_lock(1146049102,4)")) { lock.execute(); }
                result=executor.submit(()->{try {service.registrar(admin,1,Tipo.CLIENTE,req); return 201;} catch(ApiException e) {return e.status();}});
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10); boolean waiting=false;
                while(System.nanoTime()<deadline) {
                    waiting=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND classid=1146049102 AND objid=4 AND NOT granted AND database=(SELECT oid FROM pg_database WHERE datname=current_database()))",Boolean.class);
                    if(waiting) break; Thread.sleep(20);
                }
                assertThat(waiting).isTrue();
                try(var revoke=connection.prepareStatement("DELETE FROM Usuario_Rol WHERE id_usuario=?")) {revoke.setInt(1,admin); revoke.executeUpdate();}
                connection.commit(); assertThat(result.get(15,TimeUnit.SECONDS)).isEqualTo(404);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM Cobros WHERE id_factura=?",Integer.class,factura)).isZero();
            } finally {connection.rollback(); if(result!=null) result.get(15,TimeUnit.SECONDS);}
        }
    }
}
