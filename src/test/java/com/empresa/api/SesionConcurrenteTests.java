package com.empresa.api;

import com.empresa.api.security.AuthDtos;
import com.empresa.api.security.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SesionConcurrenteTests extends PostgreSqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;

    @Test
    void dosRenovacionesSimultaneasSoloPermitenUnUso() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("dorna_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dorna_demo_metadata WHERE id = 1", Integer.class)).isEqualTo(1);
        String nombre = "test-" + UUID.randomUUID();
        String clave = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES (?, ?)", nombre, encoder.encode(clave));
        int usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario = ?", Integer.class, nombre);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tokens = auth.login(new AuthDtos.Login(nombre, clave));
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            Callable<Boolean> renovar = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Fallo de coordinacion"); }
                try { auth.refresh(tokens.refreshToken()); return true; }
                catch (AuthenticationException ex) { return false; }
            };
            var first = executor.submit(renovar);
            var second = executor.submit(renovar);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT version FROM API_Sesiones WHERE id_usuario = ?", Integer.class, usuario)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM API_Sesiones WHERE id_usuario = ?", usuario);
            jdbc.update("DELETE FROM Usuarios WHERE id_usuario = ?", usuario);
        }
    }
}
