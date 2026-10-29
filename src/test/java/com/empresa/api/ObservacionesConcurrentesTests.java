package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.laboral.EmpleoService;
import com.empresa.api.observaciones.ObservacionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import javax.sql.DataSource;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static com.empresa.api.laboral.EmpleoDtos.*;
import static com.empresa.api.observaciones.ObservacionDtos.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class ObservacionesConcurrentesTests extends PostgreSqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource source;
    @Autowired ObservacionService service;
    @Autowired EmpleoService empleo;
    @Autowired Clock clock;
    int admin,worker,asignacion;
    @BeforeEach void preparar() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("dorna_test");
        String suffix=UUID.randomUUID().toString().replace("-","");
        admin=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,'inutilizable') RETURNING id_usuario",Integer.class,"test-oa-"+suffix);
        worker=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,'inutilizable') RETURNING id_usuario",Integer.class,"test-ow-"+suffix);
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",admin);
        jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) SELECT ?,1,id_rol FROM Roles WHERE nombre='TRABAJADOR'",worker);
        empleo.vincular(admin,1,301,new Vincular(worker));
        var hoy=LocalDate.now(clock);
        asignacion=empleo.asignar(admin,1,301,new NuevaAsignacion(1001,hoy,hoy)).id();
    }
    @AfterEach void limpiar() {
        // Solo registros de las identidades aleatorias de este caso.
        jdbc.update("DELETE FROM API_Observaciones_Auditoria WHERE observacion IN (SELECT id FROM API_Observaciones_Obra WHERE autor IN (?,?))",admin,worker);
        jdbc.update("DELETE FROM API_Observaciones_Obra WHERE autor IN (?,?)",admin,worker);
        jdbc.update("DELETE FROM API_Usuario_Operario WHERE id_usuario=?",worker);
        jdbc.update("DELETE FROM API_Asignaciones_Obra WHERE id IN (SELECT referencia FROM API_Laboral_Auditoria WHERE autor=? AND accion='CREAR_ASIGNACION')",admin);
        jdbc.update("DELETE FROM API_Laboral_Auditoria WHERE autor=?",admin);
        jdbc.update("DELETE FROM API_Accesos_Empresa WHERE id_usuario=?",worker);
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",admin);
        jdbc.update("DELETE FROM Usuarios WHERE id_usuario IN (?,?)",admin,worker);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void resolverSimultaneamenteNoSobrescribeNiDuplicaAuditoria(boolean mismoTexto) throws Exception {
        var o=service.crear(worker,1,1001,true,new Nueva("Revisar acceso"));
        try(var executor=Executors.newFixedThreadPool(2)) {
            var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
            var futures=new java.util.ArrayList<Future<Integer>>();
            for(int i=0;i<2;i++) {
                String texto=mismoTexto?"Revisado":"Revision "+i;
                futures.add(executor.submit(()-> {
                    ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Coordinacion agotada");
                    try { service.resolver(admin,1,1001,o.id(),new Resolver(texto)); return 200; }
                    catch(ApiException ex) { return ex.status(); }
                }));
            }
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200,mismoTexto?200:409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Observaciones_Auditoria WHERE observacion=? AND accion='RESOLVER'",Integer.class,o.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Auditoria WHERE modulo='OBSERVACIONES' AND entidad_id=? AND accion='RESOLVER' AND cambios->'antes'->>'estado'='ABIERTA' AND cambios->'despues'->>'estado'='RESUELTA'",Integer.class,o.id())).isEqualTo(1);
            assertThat(service.consultar(worker,1,1001,o.id(),true).estado()).isEqualTo(Estado.RESUELTA);
        }
    }
    @Test void cancelarAsignacionMientrasEsperaImpideCrearTrasObtenerBloqueo() throws Exception {
        try(var executor=Executors.newSingleThreadExecutor(); var connection=source.getConnection()) {
            connection.setAutoCommit(false);
            Future<Integer> result=null;
            try {
                try(var lock=connection.prepareStatement("SELECT pg_advisory_xact_lock(1146049102,4)")) { lock.execute(); }
                result=executor.submit(()-> {
                    try { service.crear(worker,1,1001,true,new Nueva("Asignacion retirada")); return 201; }
                    catch(ApiException ex) { return ex.status(); }
                });
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                boolean waiting=false;
                while(System.nanoTime()<deadline) {
                    waiting=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND classid=1146049102 AND objid=4 AND NOT granted AND database=(SELECT oid FROM pg_database WHERE datname=current_database()))",Boolean.class);
                    if(waiting) break;
                    Thread.sleep(20);
                }
                assertThat(waiting).as("La creacion espera al cambio de asignacion").isTrue();
                try(var cancel=connection.prepareStatement("UPDATE API_Asignaciones_Obra SET cancelada=TRUE WHERE id=?")) {
                    cancel.setInt(1,asignacion); assertThat(cancel.executeUpdate()).isEqualTo(1);
                }
                connection.commit();
                assertThat(result.get(15,TimeUnit.SECONDS)).isEqualTo(404);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Observaciones_Obra WHERE autor=?",Integer.class,worker)).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Observaciones_Auditoria WHERE autor=?",Integer.class,worker)).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Auditoria WHERE modulo='OBSERVACIONES' AND autor=?",Integer.class,worker)).isZero();
            } finally {
                connection.rollback();
                if(result!=null) result.get(15,TimeUnit.SECONDS);
            }
        }
    }
}
