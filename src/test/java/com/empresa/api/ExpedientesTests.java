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
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ExpedientesTests extends PostgreSqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    private String token;
    private int usuario;
    @BeforeEach
    void preparar() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("dorna_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dorna_demo_metadata WHERE id = 1", Integer.class)).isEqualTo(1);
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-expedientes', ?)", passwords.encode("clave-ficticia-pruebas"));
        usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario = 'test-expedientes'", Integer.class);
        asignar(1);
        token = auth.login(new AuthDtos.Login("test-expedientes", "clave-ficticia-pruebas")).accessToken();
    }
    private void asignar(int empresa) {
        jdbc.update("INSERT INTO API_Accesos_Empresa SELECT ?, ?, id_rol FROM Roles WHERE nombre = 'CONSULTA_RECURSOS_DEMO'", usuario, empresa);
    }
    private ResultActions getResource(String path) throws Exception {
        return mvc.perform(get("/api/v1/empresas/" + path).header("Authorization", "Bearer " + token));
    }
    private void retirar(String permiso) {
        jdbc.update("DELETE FROM Rol_Permiso WHERE id_rol = (SELECT id_rol FROM Roles WHERE nombre = 'CONSULTA_RECURSOS_DEMO') AND id_permiso = (SELECT id_permiso FROM Permisos WHERE nombre = ?)", permiso);
    }

    @Test
    void paginaFiltraYOrdenaPresupuestosSinMezclarEmpresas() throws Exception {
        getResource("1/presupuestos?size=1&sort=numero,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].id").value(4002));
        getResource("1/presupuestos?size=1&page=1&sort=numero,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(4001));
        getResource("1/presupuestos?obra=1001&cliente=101&estado=ACEPTADO&q=001").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].total").value("3630.00"));
        getResource("1/presupuestos?obra=1003").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        getResource("1/presupuestos?size=1&page=8").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void importesGuardadosYLineasConPrecisionSinRecalculos() throws Exception {
        getResource("1/presupuestos/4001").andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value("3000.00"))
                .andExpect(jsonPath("$.impuestos").value("630.00")).andExpect(jsonPath("$.total").value("3630.00"))
                .andExpect(jsonPath("$.formatoLineas").value("PLANO"));
        var result = getResource("1/presupuestos/4001/lineas?size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].cantidad").value("1.00"))
                .andReturn().getResponse().getContentAsString();
        int linea = json.readTree(result).get("items").get(0).get("id").asInt();
        getResource("1/presupuestos/4001/lineas/" + linea).andExpect(status().isOk())
                .andExpect(jsonPath("$.importe").value("1000.00"));
        getResource("1/presupuestos/4002/lineas/" + linea).andExpect(status().isNotFound());
        jdbc.update("UPDATE Presupuestos SET total = 123.45 WHERE id_presupuesto = 4001");
        getResource("1/presupuestos/4001").andExpect(status().isOk()).andExpect(jsonPath("$.total").value("123.45"));
    }

    @Test
    void presupuestoSinObraSinValidezYSinLineas() throws Exception {
        jdbc.update("UPDATE Presupuestos SET id_obra = NULL, validez = NULL WHERE id_presupuesto = 4002");
        jdbc.update("DELETE FROM Lineas_Presupuesto WHERE id_presupuesto = 4002");
        String response = getResource("1/presupuestos/4002").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(response).get("obraId").isNull()).isTrue();
        assertThat(json.readTree(response).get("validez").isNull()).isTrue();
        getResource("1/presupuestos/4002/lineas").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2/presupuestos", "1/presupuestos/4003", "1/presupuestos/4003/lineas",
            "1/presupuestos/4001/lineas/44003", "1/presupuestos/999999",
            "1/obras/1003/documentos", "1/obras/1001/documentos/7002", "1/obras/1001/documentos/7002/contenido",
            "1/obras/1002/documentos/7001/contenido", "1/obras/1001/documentos/999999/contenido"})
    void cambiarIdsOEmpresaNoDaAcceso(String path) throws Exception {
        getResource(path).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void accesoADosEmpresasMantieneLaPertenenciaDeCadaRecurso() throws Exception {
        asignar(2);
        getResource("2/presupuestos/4003").andExpect(status().isOk()).andExpect(jsonPath("$.total").value("121.00"));
        getResource("2/obras/1003/documentos/7002/contenido").andExpect(status().isOk());
        getResource("1/presupuestos/4003").andExpect(status().isNotFound());
        getResource("1/obras/1001/documentos/7002/contenido").andExpect(status().isNotFound());
    }

    @Test
    void pertenenciaYRelacionIncoherenteOcultanPresupuestoYLineas() throws Exception {
        jdbc.update("UPDATE Presupuestos SET id_obra = 1003 WHERE id_presupuesto = 4001");
        getResource("1/presupuestos/4001").andExpect(status().isNotFound());
        getResource("1/presupuestos/4001/lineas").andExpect(status().isNotFound());
        getResource("1/presupuestos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        jdbc.update("UPDATE Presupuestos SET id_obra = 1001, id_cliente = 102 WHERE id_presupuesto = 4001");
        getResource("1/presupuestos/4001").andExpect(status().isNotFound());
        jdbc.update("DELETE FROM API_Presupuesto_Empresa WHERE id_presupuesto = 4002");
        getResource("1/presupuestos/4002").andExpect(status().isNotFound());
    }

    @Test
    void permisosDePresupuestosYDocumentosSonIndependientes() throws Exception {
        retirar("PRESUPUESTOS");
        getResource("1/presupuestos").andExpect(status().isNotFound());
        getResource("1/presupuestos/4001/lineas").andExpect(status().isNotFound());
        getResource("1/obras/1001/documentos/7001/contenido").andExpect(status().isOk());
        retirar("DOCUMENTOS");
        getResource("1/obras/1001/documentos").andExpect(status().isNotFound());
        getResource("1/obras/1001/documentos/7001/contenido").andExpect(status().isNotFound());
    }

    @Test
    void documentosExigenTambienPermisoObras() throws Exception {
        retirar("OBRAS");
        getResource("1/obras/1001/documentos/7001").andExpect(status().isNotFound());
        getResource("1/obras/1001/documentos/7001/contenido").andExpect(status().isNotFound());
    }

    @Test
    void listadoYMetadatosNoExponenRutasNiClavesInternas() throws Exception {
        getResource("1/obras/1001/documentos?q=" + fixture("montaje", "archivo-001") + "&sort=nombre,desc&size=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(7001));
        getResource("1/obras/1002/documentos").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        getResource("1/obras/1001/documentos/7001").andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(fixture("Ficha de montaje DEMO.txt", "demo-public/archivo-001.txt")))
                .andExpect(jsonPath("$.ruta").doesNotExist()).andExpect(jsonPath("$.clave").doesNotExist());
    }

    @Test
    void descargaCoincideConArchivoYUsaCabecerasSeguras() throws Exception {
        var response = getResource("1/obras/1001/documentos/7001/contenido").andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"documento-7001\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse();
        try (var stream = getClass().getResourceAsStream("/demo/documentos/1/7001/c58f5263-bde1-41d9-a592-f909e0dfb871")) {
            assertThat(response.getContentAsByteArray()).isEqualTo(stream.readAllBytes());
        }
        assertThat(response.getContentLength()).isEqualTo(response.getContentAsByteArray().length);
    }

    @Test
    void rutaAntiguaYNombreMaliciosoNoControlanLaDescarga() throws Exception {
        jdbc.update("UPDATE Documentos SET ruta = '../../application.properties', nombre = ? WHERE id_documento = 7001", "archivo\r\nX-Inyectado: si");
        var response = getResource("1/obras/1001/documentos/7001/contenido").andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Inyectado")).andReturn().getResponse();
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8)).contains("DOCUMENTO FICTICIO").doesNotContain("spring.datasource");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../application.properties", "C:\\Windows\\win.ini", "http://example.invalid/a",
            "baed0adc-712f-44bc-9dd8-4497a3d01696", "00000000-0000-0000-0000-000000000000"})
    void clavesInvalidasAjenasOAusentesDevuelven404(String clave) throws Exception {
        jdbc.update("UPDATE API_Archivo_Documento SET clave = ? WHERE id_documento = 7001", clave);
        getResource("1/obras/1001/documentos/7001/contenido").andExpect(status().isNotFound());
    }

    @Test
    void archivoSinRegistroOPertenenciaAjenaNoSeDescarga() throws Exception {
        jdbc.update("UPDATE API_Archivo_Documento SET id_empresa = 2 WHERE id_documento = 7001");
        getResource("1/obras/1001/documentos/7001/contenido").andExpect(status().isNotFound());
        jdbc.update("DELETE FROM API_Archivo_Documento WHERE id_documento = 7001");
        getResource("1/obras/1001/documentos/7001").andExpect(status().isOk());
        getResource("1/obras/1001/documentos/7001/contenido").andExpect(status().isNotFound());
    }

    @Test
    void sesionRevocadaYAccesoDirectoARecursosPrivadosDenegados() throws Exception {
        mvc.perform(get("/api/v1/empresas/1/obras/1001/documentos/7001/contenido")).andExpect(status().isUnauthorized());
        mvc.perform(get("/demo/documentos/1/7001/c58f5263-bde1-41d9-a592-f909e0dfb871").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE API_Sesiones SET revocada = TRUE WHERE id_usuario = ?", usuario);
        getResource("1/obras/1001/documentos/7001/contenido").andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1/presupuestos?size=101", "1/presupuestos?obra=-1", "1/presupuestos?sort=total,asc",
            "1/presupuestos/4001/lineas?sort=precio,desc", "1/obras/1001/documentos?size=1&size=2"})
    void parametrosInvalidosRechazados(String path) throws Exception {
        getResource(path).andExpect(status().isBadRequest());
    }

    @Test
    void busquedaLiteralYEscrituraDenegada() throws Exception {
        mvc.perform(get("/api/v1/empresas/1/presupuestos").param("q", "%").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(post("/api/v1/empresas/1/presupuestos").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/empresas/1/obras/1001/documentos").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
