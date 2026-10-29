package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.gestion.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static com.empresa.api.gestion.GestionContactosObraDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest @ActiveProfiles("test")
class ContactosObraTransaccionesTests extends PostgreSqlIntegrationTest {
    @Autowired GestionContactosObraService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource source;
    @MockitoSpyBean GestionRepository repository;
    int usuario;
    String rol;
    @BeforeEach void preparar() {
        usuario=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,'inutilizable') RETURNING id_usuario",Integer.class,"test-co-"+UUID.randomUUID());
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",usuario);
        rol="Ensayo-"+usuario;
    }
    @AfterEach void limpiar() {
        reset(repository);
        jdbc.update("DELETE FROM Obra_Contacto WHERE usuario_creacion=?",usuario);
        jdbc.update("DELETE FROM API_Gestion_Auditoria WHERE autor=?",usuario);
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",usuario);
        jdbc.update("DELETE FROM Usuarios WHERE id_usuario=?",usuario);
    }
    int crear() {
        try { service.asignar(usuario,1,1001,new Asignar(101,rol,null)); return 201; }
        catch(ApiException ex) { return ex.status(); }
    }
    @Test void asignacionesSimultaneasNoDuplicanRelacionNiAuditoria() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
            Callable<Integer> tarea=()->{ready.countDown();if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException();return crear();};
            var a=executor.submit(tarea);var b=executor.submit(tarea);
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Obra_Contacto WHERE usuario_creacion=?",Integer.class,usuario)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Auditoria WHERE autor=?",Integer.class,usuario)).isEqualTo(1);
    }
    @Test void falloDeAuditoriaRevierteAltaEdicionYDesvinculacion() {
        doThrow(new IllegalStateException("Ensayo auditoria")).when(repository).auditar(eq(usuario),anyInt(),anyString(),anyInt(),anyString(),any(),any());
        assertThatThrownBy(()->service.asignar(usuario,1,1001,new Asignar(101,rol,null))).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Obra_Contacto WHERE usuario_creacion=?",Integer.class,usuario)).isZero();
        reset(repository);
        int id=service.asignar(usuario,1,1001,new Asignar(101,rol,"Original")).id();
        doThrow(new IllegalStateException("Ensayo auditoria")).when(repository).auditar(eq(usuario),anyInt(),anyString(),anyInt(),anyString(),any(),any());
        assertThatThrownBy(()->service.editar(usuario,1,1001,id,new Editar("Modificado","Modificado"))).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class);
        assertThat(jdbc.queryForObject("SELECT observaciones FROM Obra_Contacto WHERE id_obra_contacto=?",String.class,id)).isEqualTo("Original");
        assertThatThrownBy(()->service.desvincular(usuario,1,1001,id)).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Obra_Contacto WHERE id_obra_contacto=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Auditoria WHERE autor=?",Integer.class,usuario)).isEqualTo(1);
    }
    @Test void revalidaPermisosTrasEsperarBloqueo() throws Exception {
        try(var connection=source.getConnection();var executor=Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            Future<Integer> result=null;
            try {
                try(var lock=connection.prepareStatement("SELECT pg_advisory_xact_lock(1146049102,4)")) { lock.execute(); }
                result=executor.submit(this::crear);
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
                while(System.nanoTime()<deadline) {
                    waiting=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_locks WHERE locktype='advisory' AND classid=1146049102 AND objid=4 AND NOT granted AND database=(SELECT oid FROM pg_database WHERE datname=current_database()))",Boolean.class);
                    if(waiting) break;Thread.sleep(20);
                }
                assertThat(waiting).isTrue();
                try(var revoke=connection.prepareStatement("DELETE FROM Usuario_Rol WHERE id_usuario=?")) { revoke.setInt(1,usuario);revoke.executeUpdate(); }
                connection.commit();assertThat(result.get(15,TimeUnit.SECONDS)).isEqualTo(404);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM Obra_Contacto WHERE usuario_creacion=?",Integer.class,usuario)).isZero();
            } finally { connection.rollback();if(result!=null) result.get(15,TimeUnit.SECONDS); }
        }
    }
}
