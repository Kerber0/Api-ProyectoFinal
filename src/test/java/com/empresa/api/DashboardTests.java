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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class DashboardTests extends PostgreSqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    private String token;
    private int usuario;
    private static final String PERIODO = "?desde=2026-09-01&hasta=2026-10-31";

    @BeforeEach
    void preparar() {
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-dashboard', ?)",
                passwords.encode("clave-ficticia-pruebas"));
        usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario='test-dashboard'", Integer.class);
        asignar(1);
        token = auth.login(new AuthDtos.Login("test-dashboard", "clave-ficticia-pruebas")).accessToken();
    }

    private void asignar(int empresa) {
        jdbc.update("INSERT INTO API_Accesos_Empresa SELECT ?, ?, id_rol FROM Roles WHERE nombre='CONSULTA_RECURSOS_DEMO'",
                usuario, empresa);
    }

    private void retirar(String permiso) {
        jdbc.update("DELETE FROM Rol_Permiso WHERE id_rol=(SELECT id_rol FROM Roles WHERE nombre='CONSULTA_RECURSOS_DEMO') " +
                "AND id_permiso=(SELECT id_permiso FROM Permisos WHERE nombre=?)", permiso);
    }

    private ResultActions consultar(int empresa, String query) throws Exception {
        return mvc.perform(get("/api/v1/empresas/" + empresa + "/dashboard" + query)
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void consolidaIndicadoresEconomicosYRecursosSinPerderPrecision() throws Exception {
        JsonNode resumen = json.readTree(consultar(1, PERIODO).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(resumen.properties()).extracting(java.util.Map.Entry::getKey).containsExactlyInAnyOrder(
                "empresaId", "desde", "hasta", "facturacionClientes", "facturacionProveedores",
                "cobrosRegistrados", "pagosRegistrados", "obrasTotales", "presupuestosTotales");
        assertThat(resumen.get("empresaId").asInt()).isEqualTo(1);
        assertThat(resumen.get("desde").asText()).isEqualTo("2026-09-01");
        assertThat(resumen.get("hasta").asText()).isEqualTo("2026-10-31");
        assertThat(resumen.at("/facturacionClientes/total").asText()).isEqualTo("4235.00");
        assertThat(resumen.at("/facturacionProveedores/total").asText()).isEqualTo("1694.00");
        assertThat(resumen.at("/cobrosRegistrados/importe").asText()).isEqualTo("1605.00");
        assertThat(resumen.at("/pagosRegistrados/importe").asText()).isEqualTo("742.00");
        assertThat(resumen.get("obrasTotales").asLong()).isEqualTo(2);
        assertThat(resumen.get("presupuestosTotales").asLong()).isEqualTo(2);
    }

    @Test
    void contadoresAplicanLasMismasReglasDeCoherenciaQueLosListados() throws Exception {
        jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_empresa=1 AND id_cliente=101");

        consultar(1, PERIODO).andExpect(status().isOk())
                .andExpect(jsonPath("$.obrasTotales").value(0))
                .andExpect(jsonPath("$.presupuestosTotales").value(0));
    }

    @Test
    void separaLosIndicadoresPorEmpresa() throws Exception {
        asignar(2);
        consultar(2, PERIODO).andExpect(status().isOk())
                .andExpect(jsonPath("$.empresaId").value(2))
                .andExpect(jsonPath("$.obrasTotales").value(1))
                .andExpect(jsonPath("$.presupuestosTotales").value(1))
                .andExpect(jsonPath("$.facturacionClientes.total").value("121.00"))
                .andExpect(jsonPath("$.facturacionProveedores.total").value("242.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OBRAS", "PRESUPUESTOS", "INFORMES", "FACTURAS_CLIENTE",
            "FACTURAS_PROVEEDOR", "COBROS", "PAGOS"})
    void exigeTodosLosPermisosDeLosIndicadores(String permiso) throws Exception {
        retirar(permiso);
        consultar(1, PERIODO).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?desde=2026-09-01", "?hasta=2026-10-31",
            "?desde=2026-02-30&hasta=2026-10-31", "?desde=2026-10-31&hasta=2026-09-01",
            "?desde=2024-01-31&hasta=2026-01-01", "?desde=2026-09-01&hasta=2026-10-31&page=0",
            "?desde=2026-09-01&desde=2026-09-02&hasta=2026-10-31"})
    void validaElPeriodoYRechazaParametrosDesconocidosORepetidos(String query) throws Exception {
        consultar(1, query).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void protegeEmpresaSesionYMetodo() throws Exception {
        mvc.perform(get("/api/v1/empresas/1/dashboard" + PERIODO)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/empresas/1/dashboard" + PERIODO)
                .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        consultar(2, PERIODO).andExpect(status().isNotFound());
        consultar(999999, PERIODO).andExpect(status().isNotFound());
        jdbc.update("UPDATE API_Sesiones SET revocada=TRUE WHERE id_usuario=?", usuario);
        consultar(1, PERIODO).andExpect(status().isUnauthorized());
    }
}
