package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class GestionUsuariosConcurrenteTests extends PostgreSqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired GestionUsuariosService service;
    @Autowired AuthService auth;
    @Autowired AccesoRepository accesos;
    int crear() {
        return jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,'hash-no-utilizable') RETURNING id_usuario",
                Integer.class,"test-gc-"+UUID.randomUUID().toString().replace("-",""));
    }
    void borrar(int id) {
        jdbc.update("DELETE FROM API_Usuarios_Auditoria WHERE autor=? OR usuario=?",id,id);
        jdbc.update("DELETE FROM API_Sesiones WHERE id_usuario=?",id);
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",id);
        jdbc.update("DELETE FROM Usuarios WHERE id_usuario=?",id);
    }
    @Test void dosBajasSimultaneasConservanExactamenteUnAdministrador() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("dorna_test");
        int rol=jdbc.queryForObject("SELECT id_rol FROM Roles WHERE nombre='ADMIN'",Integer.class);
        var previos=jdbc.queryForList("SELECT id_usuario FROM Usuario_Rol WHERE id_rol=?",Integer.class,rol);
        int first=crear(), second=crear();
        try(var executor=Executors.newFixedThreadPool(2)) {
            jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) VALUES (?,?),(?,?)",first,rol,second,rol);
            jdbc.update("DELETE FROM Usuario_Rol WHERE id_rol=? AND id_usuario NOT IN (?,?)",rol,first,second);
            var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
            java.util.function.IntFunction<Callable<Integer>> task=id->()-> {
                ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Coordinacion agotada");
                try { service.asignar(id,id,null,rol,false); return 204; }
                catch(ApiException ex) { return ex.status(); }
            };
            var a=executor.submit(task.apply(first)); var b=executor.submit(task.apply(second));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(java.util.List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM Usuario_Rol WHERE id_rol=?",Integer.class,rol)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Usuarios_Auditoria WHERE usuario IN (?,?)",Integer.class,first,second)).isEqualTo(1);
        } finally {
            for(int id:previos) jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) VALUES (?,?) ON CONFLICT DO NOTHING",id,rol);
            borrar(first); borrar(second);
        }
    }
    @Test void resetPasswordYRefreshConcurrentesNoDejanSesionAnteriorVigente() throws Exception {
        int rol=jdbc.queryForObject("SELECT id_rol FROM Roles WHERE nombre='ADMIN'",Integer.class);
        int admin=crear(), target=crear();
        String nombre=jdbc.queryForObject("SELECT nombre_usuario FROM Usuarios WHERE id_usuario=?",String.class,target);
        try(var executor=Executors.newFixedThreadPool(2)) {
            jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) VALUES (?,?)",admin,rol);
            service.password(admin,target,new GestionUsuariosDtos.Password("clave-ficticia-original"));
            var old=auth.login(new AuthDtos.Login(nombre,"clave-ficticia-original"));
            var start=new CountDownLatch(1);
            var reset=executor.submit(()-> { start.await(); service.password(admin,target,new GestionUsuariosDtos.Password("clave-ficticia-nueva")); return true; });
            var refresh=executor.submit(()-> {
                start.await();
                try { return auth.refresh(old.refreshToken()); }
                catch(org.springframework.security.authentication.BadCredentialsException ex) { return null; }
            });
            start.countDown();
            assertThat(reset.get(15,TimeUnit.SECONDS)).isTrue(); refresh.get(15,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Sesiones WHERE id_usuario=? AND NOT revocada",Integer.class,target)).isZero();
            assertThatThrownBy(()->auth.login(new AuthDtos.Login(nombre,"clave-ficticia-original")))
                    .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
            assertThat(auth.login(new AuthDtos.Login(nombre,"clave-ficticia-nueva")).accessToken()).isNotBlank();
        } finally {
            jdbc.update("UPDATE Usuarios SET usuario_modificacion=NULL WHERE id_usuario=?",target);
            borrar(target); borrar(admin);
        }
    }
}
