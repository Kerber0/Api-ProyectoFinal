package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.documentos.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest @ActiveProfiles("test")
class DocumentosConcurrentesTests extends PostgreSqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource source;
    @Autowired DocumentoService service;
    @MockitoSpyBean DocumentoRepository repository;
    @MockitoSpyBean ArchivoLocalStore store;
    int admin;
    @BeforeEach void preparar() {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("dorna_test");
        admin=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,'inutilizable') RETURNING id_usuario",Integer.class,"test-doc-"+UUID.randomUUID());
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",admin);
    }
    @AfterEach void limpiar() {
        reset(store,repository);
        jdbc.queryForList("SELECT clave FROM API_Documentos WHERE autor=?",String.class,admin).forEach(store::borrar);
        jdbc.update("DELETE FROM API_Documentos_Auditoria WHERE autor=?",admin);
        jdbc.update("DELETE FROM API_Documentos WHERE autor=?",admin);
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",admin);
        jdbc.update("DELETE FROM Usuarios WHERE id_usuario=?",admin);
    }
    DocumentoDtos.Detalle crear() { return service.crear(admin,1,"obras",1001,"application/pdf",DocumentosTests.nombre("prueba.pdf"),DocumentosTests.PDF); }
    @Test void falloTrasPersistenciaRevierteMetadatosAuditoriaYArchivo() {
        doAnswer(call->{call.callRealMethod();throw new org.springframework.dao.DataIntegrityViolationException("Fallo simulado tras auditar");}).when(repository).guardar(any());
        assertThatThrownBy(this::crear).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var clave=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store).guardar(clave.capture(),any());
        verify(store).borrar(clave.getValue());
        assertThatThrownBy(()->store.leer(clave.getValue())).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Documentos WHERE autor=?",Integer.class,admin)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Documentos_Auditoria WHERE autor=?",Integer.class,admin)).isZero();
    }
    @Test void falloDelAlmacenNoPublicaMetadatosNiAuditoria() {
        doThrow(ApiException.almacenNoDisponible()).when(store).guardar(any(),any());
        assertThatThrownBy(this::crear).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Documentos WHERE autor=?",Integer.class,admin)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Documentos_Auditoria WHERE autor=?",Integer.class,admin)).isZero();
    }
    @Test void subidasConcurrentesConMismoNombreConservanArchivosIndependientes() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var inicio=new CountDownLatch(1);
            Callable<DocumentoDtos.Detalle> tarea=()->{if(!inicio.await(10,TimeUnit.SECONDS))throw new IllegalStateException();return crear();};
            var a=executor.submit(tarea); var b=executor.submit(tarea); inicio.countDown();
            var primero=a.get(15,TimeUnit.SECONDS); var segundo=b.get(15,TimeUnit.SECONDS);
            assertThat(primero.id()).isNotEqualTo(segundo.id());
            for(var d:java.util.List.of(primero,segundo)) assertThat(service.descargar(admin,1,"obras",1001,d.id(),false).bytes()).isEqualTo(DocumentosTests.PDF);
            assertThat(jdbc.queryForObject("SELECT count(DISTINCT clave) FROM API_Documentos WHERE autor=?",Integer.class,admin)).isEqualTo(2);
        }
    }
    @Test void revocarRolMientrasEsperaImpideEscrituraDeArchivo() throws Exception {
        try(var executor=Executors.newSingleThreadExecutor(); var connection=source.getConnection()) {
            connection.setAutoCommit(false); Future<Integer> result=null;
            try {
                try(var lock=connection.prepareStatement("SELECT pg_advisory_xact_lock(1146049102,4)")) { lock.execute(); }
                result=executor.submit(()->{try{crear();return 201;}catch(ApiException ex){return ex.status();}});
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10); boolean waiting=false;
                while(System.nanoTime()<deadline) {
                    waiting=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND classid=1146049102 AND objid=4 AND NOT granted AND database=(SELECT oid FROM pg_database WHERE datname=current_database()))",Boolean.class);
                    if(waiting)break;Thread.sleep(20);
                }
                assertThat(waiting).isTrue();
                try(var statement=connection.prepareStatement("DELETE FROM Usuario_Rol WHERE id_usuario=?")) {statement.setInt(1,admin);statement.executeUpdate();}
                connection.commit(); assertThat(result.get(15,TimeUnit.SECONDS)).isEqualTo(404);
                verify(store,never()).guardar(any(),any());
                assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Documentos WHERE autor=?",Integer.class,admin)).isZero();
            } finally {connection.rollback();if(result!=null)result.get(15,TimeUnit.SECONDS);}
        }
    }
}
