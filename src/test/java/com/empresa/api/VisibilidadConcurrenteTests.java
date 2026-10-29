package com.empresa.api;

import com.empresa.api.maestros.MaestroService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest @ActiveProfiles("test")
class VisibilidadConcurrenteTests extends PostgreSqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MaestroService service;

    @Test void dosConcesionesConcurrentesCreanUnaRelacionYUnaAuditoria() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("dorna_test");
        jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_cliente=101 AND id_empresa=2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Cliente_Empresa WHERE id_cliente=101 AND id_empresa=2", Integer.class)).isZero();
        int usuario = jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?, 'hash-no-utilizable') RETURNING id_usuario", Integer.class, "test-visibilidad-" + UUID.randomUUID().toString().replace("-", ""));
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int empresa : new int[]{1,2}) {
                jdbc.update("INSERT INTO API_Accesos_Empresa SELECT ?,?,id_rol FROM Roles WHERE nombre='ADMIN'", usuario, empresa);
            }
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            Callable<Boolean> conceder = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Fallo de coordinacion"); }
                service.compartirCliente(usuario, 1, 101, 2);
                return true;
            };
            var first = executor.submit(conceder);
            var second = executor.submit(conceder);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Cliente_Empresa WHERE id_cliente=101 AND id_empresa=2", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Visibilidad_Auditoria WHERE id_usuario=?", Integer.class, usuario)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM API_Visibilidad_Auditoria WHERE id_usuario=?", usuario);
            jdbc.update("INSERT INTO API_Cliente_Empresa(id_cliente,id_empresa) VALUES (101,2) ON CONFLICT DO NOTHING");
            jdbc.update("DELETE FROM API_Accesos_Empresa WHERE id_usuario=?", usuario);
            jdbc.update("DELETE FROM Usuarios WHERE id_usuario=?", usuario);
        }
    }
}
