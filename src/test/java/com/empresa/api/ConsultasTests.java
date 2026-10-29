package com.empresa.api;

import com.empresa.api.security.AuthDtos;
import com.empresa.api.security.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ConsultasTests extends PostgreSqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    private String token;
    private int usuario;

    @BeforeEach
    void preparar() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("dorna_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dorna_demo_metadata WHERE id = 1", Integer.class)).isEqualTo(1);
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-consultas', ?)", passwords.encode("clave-solo-pruebas"));
        usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario = 'test-consultas'", Integer.class);
        asignar(1);
        token = auth.login(new AuthDtos.Login("test-consultas", "clave-solo-pruebas")).accessToken();
    }
    private void asignar(int empresa) {
        jdbc.update("INSERT INTO API_Accesos_Empresa SELECT ?, ?, id_rol FROM Roles WHERE nombre = 'CONSULTA_RECURSOS_DEMO'", usuario, empresa);
    }
    private ResultActions consultar(String path) throws Exception {
        return mvc.perform(get("/api/v1/empresas/" + path).header("Authorization", "Bearer " + token));
    }

    @Test
    void listaYDetalleDeClienteConContactosYDireccion() throws Exception {
        consultar("1/clientes").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(101)).andExpect(jsonPath("$.items[0].nombre")
                        .value(fixture("Cliente Norte [DEMO]", "Cliente Ejemplo 001 SL")));
        consultar("1/clientes/101").andExpect(status().isOk()).andExpect(jsonPath("$.nifCif").value("DEMO-CLI-001"))
                .andExpect(jsonPath("$.limiteCredito").doesNotExist()).andExpect(jsonPath("$.observaciones")
                        .value(fixture("DATOS FICTICIOS.", "Cliente sintético para pruebas")))
                .andExpect(jsonPath("$.ibanEnmascarado").isEmpty());
        consultar("1/clientes/101/contactos").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(101))
                .andExpect(jsonPath("$.items[0].departamento").isEmpty());
        consultar("1/clientes/101/direcciones").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].direccion")
                        .value(fixture("Calle Ficticia 100 [DEMO]", "Calle Ejemplo 001")))
                .andExpect(jsonPath("$.items[0].tipoDireccion").value("OBRA"));
    }

    @Test
    void paginaObrasConOrdenEstableYTotalesFiltrados() throws Exception {
        consultar("1/obras?size=1&page=0&sort=codigo,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1002)).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.sort").value("codigo,desc"));
        consultar("1/obras?size=1&page=1&sort=codigo,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(1001));
        consultar("1/obras?size=1&page=2").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(2));
        consultar("1/obras?estado=EN_CURSO&cliente=101&q=" + fixture("estructura", "006")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(1001));
        consultar("1/obras?cliente=102").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        jdbc.update("UPDATE Obras SET titulo = 'Igual' WHERE id_obra IN (1001, 1002)");
        consultar("1/obras?size=1&sort=titulo,asc").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(1001));
        consultar("1/obras?size=1&page=1&sort=titulo,asc").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(1002));
    }

    @Test
    void detalleDeObraConImporteExactoYContactos() throws Exception {
        consultar("1/obras/1001").andExpect(status().isOk()).andExpect(jsonPath("$.presupuesto").value("3000.00"))
                .andExpect(jsonPath("$.cliente.id").value(101)).andExpect(jsonPath("$.direccion.id").value(101))
                .andExpect(jsonPath("$.fechaInicio").value("2026-09-01"));
        consultar("1/obras/1001/contactos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].contacto.id").value(101));
    }

    @Test
    void datosIncompletosSeConservanComoNulosYListasVacias() throws Exception {
        asignar(2);
        String cliente = consultar("2/clientes/102").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(cliente).get("email").isNull()).isTrue();
        consultar("2/clientes/102/contactos").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("2/clientes/102/direcciones").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        String obra = consultar("2/obras/1003").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tree = json.readTree(obra);
        assertThat(tree.get("direccion").isNull()).isTrue();
        assertThat(tree.get("fechaInicio").isNull()).isTrue();
        assertThat(tree.get("fechaFinPrevista").isNull()).isTrue();
        consultar("2/obras/1003/contactos").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2/clientes", "2/obras", "1/obras/1003",
            "1/obras/1003/contactos", "1/clientes/999999", "999999/obras"})
    void cambiarEmpresaOIdDeRecursosNoPermiteLeerlos(String path) throws Exception {
        consultar(path).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void accesoADosEmpresasNoPermiteMezclarSusRutas() throws Exception {
        asignar(2);
        consultar("2/clientes").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(101));
        consultar("1/clientes/102").andExpect(status().isOk());
        consultar("2/obras/1001").andExpect(status().isNotFound());
    }

    @Test
    void permisosSeparadosYRetiradaInmediata() throws Exception {
        jdbc.update("DELETE FROM Rol_Permiso WHERE id_permiso = (SELECT id_permiso FROM Permisos WHERE nombre = 'CLIENTES')");
        consultar("1/clientes").andExpect(status().isNotFound());
        consultar("1/obras").andExpect(status().isOk());
        consultar("1/obras/1001/contactos").andExpect(status().isNotFound());
        jdbc.update("DELETE FROM API_Accesos_Empresa WHERE id_usuario = ?", usuario);
        consultar("1/obras").andExpect(status().isNotFound());
    }

    @Test
    void rolDeOtraEmpresaNoPrestaSusPermisos() throws Exception {
        jdbc.update("DELETE FROM API_Accesos_Empresa WHERE id_usuario = ?", usuario);
        jdbc.update("INSERT INTO API_Accesos_Empresa SELECT ?, 1, id_rol FROM Roles WHERE nombre = 'CONSULTA_EMPRESA_DEMO'", usuario);
        asignar(2);
        consultar("1/clientes").andExpect(status().isNotFound());
        consultar("1/obras").andExpect(status().isNotFound());
        consultar("2/obras").andExpect(status().isOk());
    }

    @Test
    void relacionesInconsistentesNoExponenDatosAjenos() throws Exception {
        jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_cliente = 102 AND id_empresa = 1");
        jdbc.update("UPDATE Obras SET id_cliente = 102 WHERE id_obra = 1001");
        consultar("1/obras/1001").andExpect(status().isNotFound());
        consultar("1/obras/1001/contactos").andExpect(status().isNotFound());
        consultar("1/obras").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        jdbc.update("UPDATE Obras SET id_cliente = 101 WHERE id_obra = 1001");
        jdbc.update("UPDATE Direcciones SET id_entidad = 102 WHERE id_direccion = 101");
        jdbc.update("UPDATE Contactos SET id_entidad = 102 WHERE id_contacto = 101");
        var result = consultar("1/obras/1001").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(result).get("direccion").isNull()).isTrue();
        consultar("1/obras/1001/contactos").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void recursoSinPertenenciaNoSeExpone() throws Exception {
        jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_cliente = 101");
        consultar("1/clientes/101").andExpect(status().isNotFound());
        consultar("1/obras/1001").andExpect(status().isNotFound());
        consultar("1/clientes").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(102))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"size=0", "size=101", "page=-1", "page=100001", "page=abc", "sort=desconocido,asc",
            "sort=id,random", "size=1&size=2", "empresa=2", "cliente=-1"})
    void parametrosInvalidosDevuelven400(String query) throws Exception {
        consultar("1/obras?" + query).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.campos").isNotEmpty());
    }

    @Test
    void busquedaLiteralSinComodinesNiInyeccion() throws Exception {
        mvc.perform(get("/api/v1/empresas/1/clientes").param("q", "x".repeat(101)).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        for (String q : new String[]{"%", "_", "' OR 1=1 --", "!"}) {
            mvc.perform(get("/api/v1/empresas/1/clientes").param("q", q).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        }
        consultar("1/clientes?q=" + fixture("Norte", "001") + "&sort=nombre,asc")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void erroresYRechazosDeSeguridadCompartenIdDePeticion() throws Exception {
        var invalid = consultar("1/obras?size=0").andExpect(status().isBadRequest()).andReturn().getResponse();
        assertThat(json.readTree(invalid.getContentAsString()).get("requestId").asText()).isEqualTo(invalid.getHeader("X-Request-Id"));
        var unauthenticated = mvc.perform(get("/api/v1/empresas/1/obras")).andExpect(status().isUnauthorized()).andReturn().getResponse();
        assertThat(json.readTree(unauthenticated.getContentAsString()).get("requestId").asText())
                .isEqualTo(unauthenticated.getHeader("X-Request-Id")).isNotBlank();
        mvc.perform(post("/api/v1/empresas/1/clientes").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void contratoOpenApiDisponibleConAutenticacion() throws Exception {
        mvc.perform(get("/api/v1/openapi.json")).andExpect(status().isUnauthorized());
        var response = mvc.perform(get("/api/v1/openapi.json").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var spec = json.readTree(response);
        assertThat(spec.get("openapi").asText()).isEqualTo("3.1.1");
        assertThat(spec.get("paths").has("/empresas/{empresa}/obras/{id}")).isTrue();
        assertThat(spec.get("components").get("schemas").has("PaginaObras")).isTrue();
    }

    @Test
    void numeroDeConsultasNoCreceConElTamanoDePagina() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(JdbcTemplate.class);
        var previousLevel = logger.getLevel();
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start();
        logger.addAppender(capture);
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            for (int size : new int[]{1, 100}) {
                capture.list.clear();
                consultar("1/obras?size=" + size).andExpect(status().isOk());
                long statements = capture.list.stream()
                        .filter(event -> event.getFormattedMessage().startsWith("Executing prepared SQL statement"))
                        .count();
                // Sesion, permiso, COUNT y pagina con joins; ninguna consulta por fila.
                assertThat(statements).isEqualTo(4);
            }
        } finally {
            logger.setLevel(previousLevel);
            logger.detachAppender(capture);
            capture.stop();
        }
    }
}
