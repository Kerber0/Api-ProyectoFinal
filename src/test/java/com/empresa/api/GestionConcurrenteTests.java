package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.gestion.GestionMaestrosService;
import com.empresa.api.gestion.GestionObrasService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.empresa.api.gestion.GestionDtos.*;
import static com.empresa.api.facturacion.FacturacionDtos.EntradaLinea;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class GestionConcurrenteTests extends PostgreSqlIntegrationTest {
    @Autowired GestionMaestrosService maestros;
    @Autowired GestionObrasService obras;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource source;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Clock clock;
    int admin, cliente;
    LocalDate hoy;

    @BeforeEach void preparar() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("dorna_test");
        hoy=LocalDate.now(clock);
        admin=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,'inutilizable') RETURNING id_usuario",
            Integer.class,"test-gnc-"+UUID.randomUUID());
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",admin);
        cliente=maestros.crearCliente(admin,1,entrada(null,"GC-"+admin)).id();
    }
    ClienteEntrada entrada(Long version,String codigo) {
        return new ClienteEntrada(version,codigo,new Identidad("EMPRESA",null,null,"Prueba concurrente","GC-"+admin,null,null),"0",null,null,null);
    }
    SerieFicha serie(TipoSerie tipo) {
        return obras.crearSerie(admin,1,tipo,new SerieEntrada("Concurrente "+admin,"GC",hoy.getYear(),4));
    }
    String crear(TipoSerie tipo,int serie) {
        if(tipo==TipoSerie.OBRA) return obras.crearObra(admin,1,new ObraEntrada(null,serie,cliente,"Prueba",null,"100",hoy,null)).codigo();
        return obras.crearPresupuesto(admin,1,new PresupuestoEntrada(null,serie,cliente,null,hoy,null,
            List.of(new EntradaLinea("Prueba","1","100","0","21")))).numero();
    }
    @AfterEach void limpiar() {
        // Solo fixtures de esta cuenta. La proyeccion inmutable de auditoria permanece.
        jdbc.update("DELETE FROM API_Gestion_Auditoria WHERE autor=?",admin);
        jdbc.update("DELETE FROM Lineas_Presupuesto WHERE id_presupuesto IN (SELECT id_presupuesto FROM Presupuestos WHERE usuario_creacion=?)",admin);
        jdbc.update("DELETE FROM API_Presupuesto_Empresa WHERE id_presupuesto IN (SELECT id_presupuesto FROM Presupuestos WHERE usuario_creacion=?)",admin);
        jdbc.update("DELETE FROM Presupuestos WHERE usuario_creacion=?",admin);
        jdbc.update("DELETE FROM Historial_Estados_Obra WHERE usuario_creacion=?",admin);
        jdbc.update("DELETE FROM API_Obra_Empresa WHERE id_obra IN (SELECT id_obra FROM Obras WHERE usuario_creacion=?)",admin);
        jdbc.update("DELETE FROM Obras WHERE usuario_creacion=?",admin);
        jdbc.update("DELETE FROM Series_Documentos WHERE usuario_creacion=?",admin);
        jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_cliente IN (SELECT id_cliente FROM Clientes WHERE usuario_creacion=?)",admin);
        jdbc.update("DELETE FROM Clientes WHERE usuario_creacion=?",admin);
        jdbc.update("DELETE FROM Entidades WHERE usuario_creacion=?",admin);
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",admin);
        jdbc.update("DELETE FROM Usuarios WHERE id_usuario=?",admin);
    }
    <T> List<T> simultaneas(Callable<T> tarea) throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
            Callable<T> coordinada=()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Coordinacion agotada");return tarea.call();};
            var a=executor.submit(coordinada);var b=executor.submit(coordinada);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            return List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }
    }
    @Test void dosEdicionesConLaMismaVersionConfirmanSoloUna() throws Exception {
        var resultados=simultaneas(()->{
            try { maestros.actualizarCliente(admin,1,cliente,entrada(0L,"EDIT-"+admin));return 200; }
            catch(ApiException e) { return e.status(); }
        });
        assertThat(resultados).containsExactlyInAnyOrder(200,409);
        assertThat(maestros.cliente(admin,1,cliente).version()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Auditoria WHERE autor=? AND modulo='GESTION' AND accion='ACTUALIZAR'",Integer.class,admin)).isEqualTo(1);
    }
    @ParameterizedTest @EnumSource(TipoSerie.class)
    void altasSimultaneasConservanNumeracionYAuditoria(TipoSerie tipo) throws Exception {
        int serie=serie(tipo).id();
        assertThat(simultaneas(()->crear(tipo,serie))).doesNotHaveDuplicates().allMatch(n->n.endsWith("0001")||n.endsWith("0002"));
        assertThat(jdbc.queryForObject("SELECT siguiente_numero FROM Series_Documentos WHERE id_serie=?",Integer.class,serie)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Auditoria WHERE autor=? AND modulo='GESTION' AND entidad=?",Integer.class,admin,tipo.name())).isEqualTo(2);
    }
    @ParameterizedTest @EnumSource(TipoSerie.class)
    void falloDeAuditoriaRevierteAltaLineasHistorialYContador(TipoSerie tipo) {
        int serie=serie(tipo).id();
        var tx=new TransactionTemplate(transactions);
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            provocarConflictoAuditoria();
            crear(tipo,serie);
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT siguiente_numero FROM Series_Documentos WHERE id_serie=?",Integer.class,serie)).isEqualTo(1);
        for(String tabla:List.of("Obras","Presupuestos","Lineas_Presupuesto","Historial_Estados_Obra"))
            assertThat(jdbc.queryForObject("SELECT count(*) FROM "+tabla+" WHERE usuario_creacion=?",Integer.class,admin)).as(tabla).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Auditoria WHERE autor=? AND entidad=?",Integer.class,admin,tipo.name())).isZero();
        assertThat(crear(tipo,serie)).endsWith("0001");
    }
    @Test void falloDeAuditoriaRevierteIdentidadYVersion() {
        var anterior=maestros.cliente(admin,1,cliente);
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->{
            provocarConflictoAuditoria();
            maestros.actualizarCliente(admin,1,cliente,entrada(0L,"CAMBIO-"+admin));
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(maestros.cliente(admin,1,cliente)).isEqualTo(anterior);
    }
    void provocarConflictoAuditoria() {
        // La colision se revierte con la transaccion. No necesita permisos DDL
        // ni modifica/borrar registros del historial inmutable existente.
        jdbc.query("SELECT pg_advisory_xact_lock(1146049102,4)",rs->{});
        long reservado=jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('api_gestion_auditoria','id'))",Long.class);
        jdbc.update("INSERT INTO API_Auditoria(modulo,origen_id,autor,empresa,entidad,entidad_id,accion,fecha,historico,cambios) VALUES ('GESTION',?,?,1,'CLIENTE',?,'PRUEBA',CURRENT_TIMESTAMP,false,'{}'::jsonb)",reservado+1,admin,cliente);
    }
    @Test void revocacionDuranteEsperaImpideEdicion() throws Exception {
        try(var executor=Executors.newSingleThreadExecutor();var connection=source.getConnection()) {
            connection.setAutoCommit(false);Future<Integer> result=null;
            try {
                try(var lock=connection.prepareStatement("SELECT pg_advisory_xact_lock(1146049102,4)")) { lock.execute(); }
                result=executor.submit(()->{
                    try { maestros.actualizarCliente(admin,1,cliente,entrada(0L,"REVOCADO-"+admin));return 200; }
                    catch(ApiException e) { return e.status(); }
                });
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
                while(System.nanoTime()<deadline) {
                    waiting=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND classid=1146049102 AND objid=4 AND NOT granted AND database=(SELECT oid FROM pg_database WHERE datname=current_database()))",Boolean.class);
                    if(waiting) break;Thread.sleep(20);
                }
                assertThat(waiting).isTrue();
                try(var revoke=connection.prepareStatement("DELETE FROM Usuario_Rol WHERE id_usuario=?")) { revoke.setInt(1,admin);revoke.executeUpdate(); }
                connection.commit();assertThat(result.get(15,TimeUnit.SECONDS)).isEqualTo(404);
                assertThat(jdbc.queryForObject("SELECT version_gestion FROM Clientes WHERE id_cliente=?",Long.class,cliente)).isZero();
            } finally { connection.rollback();if(result!=null) result.get(15,TimeUnit.SECONDS); }
        }
    }
}
