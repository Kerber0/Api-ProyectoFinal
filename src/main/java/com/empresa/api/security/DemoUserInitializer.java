package com.empresa.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DemoUserInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String password;

    public DemoUserInitializer(JdbcTemplate jdbc, PasswordEncoder encoder,
                               @Value("${dorna.demo.password}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!"dorna_dev".equals(jdbc.queryForObject("SELECT current_database()", String.class))
                || jdbc.queryForObject("SELECT COUNT(*) FROM dorna_demo_metadata WHERE id = 1", Integer.class) != 1) {
            throw new IllegalStateException("La cuenta DEMO requiere dorna_dev marcada como demostracion");
        }
        // Nunca reemplazar claves, reactivar usuarios ni restituir permisos retirados.
        if (jdbc.queryForObject("SELECT COUNT(*) FROM Usuarios", Integer.class) != 0) { return; }
        if (!password.matches("[\\x21-\\x7E]{32,72}")) {
            throw new IllegalStateException("La clave DEMO debe ser aleatoria y tener entre 32 y 72 caracteres ASCII");
        }
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('demo', ?)", encoder.encode(password));
        jdbc.update("""
                INSERT INTO API_Accesos_Empresa (id_usuario, id_empresa, id_rol)
                SELECT u.id_usuario, 1, r.id_rol FROM Usuarios u CROSS JOIN Roles r
                WHERE u.nombre_usuario = 'demo' AND r.nombre = 'CONSULTA_RECURSOS_DEMO'
                """);
    }
}
