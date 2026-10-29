package com.empresa.api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.security.SecureRandom;
import java.sql.DriverManager;
import java.util.HexFormat;

/** PostgreSQL real para los tests SQL. No existe fallback a una base persistente. */
abstract class PostgreSqlIntegrationTest {
    private static final class Database {
        static final PostgreSQLContainer CONTAINER;
        static final String URL;
        static final String USER;
        static final String PASSWORD;
        static final String ADMIN_USER;
        static final String ADMIN_PASSWORD;

        static {
            String external = System.getenv("DORNA_TEST_DB_URL");
            if (external == null || external.isBlank()) {
                CONTAINER = new PostgreSQLContainer("postgres:18.6-bookworm")
                        .withDatabaseName("dorna_test").withUsername("dorna_instalador")
                        .withPassword(randomSecret()).withCommand("postgres", "-c", "timezone=UTC");
                CONTAINER.start();
                URL = CONTAINER.getJdbcUrl();
                USER = "dorna_test";
                PASSWORD = randomSecret();
                ADMIN_USER = CONTAINER.getUsername();
                ADMIN_PASSWORD = CONTAINER.getPassword();
                prepareContainer();
            } else {
                if (!"true".equalsIgnoreCase(System.getenv("DORNA_TEST_DB_DISPOSABLE"))) {
                    throw new IllegalStateException("Una URL externa requiere DORNA_TEST_DB_DISPOSABLE=true");
                }
                if (!external.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/dorna_test(?:\\?.*)?")) {
                    throw new IllegalStateException("Los tests externos requieren loopback y una base llamada dorna_test");
                }
                CONTAINER = null;
                URL = external;
                USER = required("DORNA_TEST_DB_USER");
                PASSWORD = required("DORNA_TEST_DB_PASSWORD");
                ADMIN_USER = required("DORNA_TEST_DB_ADMIN_USER");
                ADMIN_PASSWORD = System.getenv().getOrDefault("DORNA_TEST_DB_ADMIN_PASSWORD", "");
                verifyExternalDatabase();
            }
        }

        private static void prepareContainer() {
            try (var connection = DriverManager.getConnection(URL, ADMIN_USER, ADMIN_PASSWORD);
                 var statement = connection.createStatement()) {
                statement.execute("CREATE ROLE dorna_test LOGIN PASSWORD '" + PASSWORD + "'");
                statement.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
                statement.execute("GRANT USAGE ON SCHEMA public TO dorna_test");
                statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO dorna_test");
                statement.execute("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE,SELECT,UPDATE ON SEQUENCES TO dorna_test");
            } catch (Exception ex) {
                throw new IllegalStateException("No se pudo preparar el usuario del contenedor", ex);
            }
            CONTAINER.copyFileToContainer(MountableFile.forClasspathResource("db/base de datos.sql"), "/tmp/dorna-base.sql");
            CONTAINER.copyFileToContainer(MountableFile.forClasspathResource("db/base de datos de prueba.sql"), "/tmp/dorna-pruebas.sql");
            executeSql("/tmp/dorna-base.sql");
            executeSql("/tmp/dorna-pruebas.sql");
            try (var connection = DriverManager.getConnection(URL, ADMIN_USER, ADMIN_PASSWORD);
                 var statement = connection.createStatement()) {
                statement.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO dorna_test");
                statement.execute("GRANT USAGE,SELECT,UPDATE ON ALL SEQUENCES IN SCHEMA public TO dorna_test");
                statement.execute("REVOKE INSERT,UPDATE,DELETE ON dorna_demo_metadata FROM dorna_test");
            } catch (Exception ex) {
                throw new IllegalStateException("No se pudieron aplicar los permisos del contenedor", ex);
            }
        }

        private static void executeSql(String file) {
            try {
                var result = CONTAINER.execInContainer("psql", "-v", "ON_ERROR_STOP=1", "-U", ADMIN_USER,
                        "-d", CONTAINER.getDatabaseName(), "-f", file);
                if (result.getExitCode() != 0) {
                    throw new IllegalStateException("Falló la inicialización SQL: " + result.getStderr());
                }
            } catch (Exception ex) {
                throw new IllegalStateException("No se pudo ejecutar " + file + " en el contenedor", ex);
            }
        }

        private static void verifyExternalDatabase() {
            try (var connection = DriverManager.getConnection(URL, ADMIN_USER, ADMIN_PASSWORD);
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT current_database(), to_regclass('public.dorna_demo_metadata') IS NOT NULL")) {
                if (!result.next() || !"dorna_test".equals(result.getString(1)) || !result.getBoolean(2)) {
                    throw new IllegalStateException("La base desechable no está inicializada con los SQL de Dorna");
                }
            } catch (Exception ex) {
                throw new IllegalStateException("No se pudo verificar la base PostgreSQL desechable", ex);
            }
        }
    }

    static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Falta variable " + key);
        return value;
    }

    static String randomSecret() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static String fixture(String ignoredLegacyValue, String currentValue) { return currentValue; }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> Database.URL);
        registry.add("spring.datasource.username", () -> Database.USER);
        registry.add("spring.datasource.password", () -> Database.PASSWORD);
        registry.add("dorna.test.admin-user", () -> Database.ADMIN_USER);
        registry.add("dorna.test.admin-password", () -> Database.ADMIN_PASSWORD);
        registry.add("dorna.security.secret", PostgreSqlIntegrationTest::randomSecret);
    }
}
