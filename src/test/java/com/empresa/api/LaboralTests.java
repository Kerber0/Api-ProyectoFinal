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
class LaboralTests extends PostgreSqlIntegrationTest {
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
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-laboral', ?)", passwords.encode("clave-ficticia-pruebas"));
        usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario = 'test-laboral'", Integer.class);
        asignar(1);
        token = auth.login(new AuthDtos.Login("test-laboral", "clave-ficticia-pruebas")).accessToken();
    }
    private void asignar(int empresa) {
        jdbc.update("INSERT INTO API_Accesos_Empresa SELECT ?, ?, id_rol FROM Roles WHERE nombre = 'CONSULTA_RECURSOS_DEMO'", usuario, empresa);
    }
    private ResultActions consultar(String path) throws Exception {
        return mvc.perform(get("/api/v1/empresas/" + path).header("Authorization", "Bearer " + token));
    }
    private void retirar(String permiso) {
        jdbc.update("DELETE FROM Rol_Permiso WHERE id_rol = (SELECT id_rol FROM Roles WHERE nombre = 'CONSULTA_RECURSOS_DEMO') AND id_permiso = (SELECT id_permiso FROM Permisos WHERE nombre = ?)", permiso);
    }
    private int primerParte() {
        return jdbc.queryForObject("SELECT MIN(id_parte) FROM Partes_Mano_Obra WHERE id_operario = 301", Integer.class);
    }

    @Test
    void fichaMinimaNoExponeDatosPrivadosNiCostes() throws Exception {
        jdbc.update("UPDATE Operarios SET nss = 'PRIVADO-NSS' WHERE id_operario = 301");
        jdbc.update("UPDATE Entidades SET observaciones = 'PRIVADO', fecha_nacimiento = '1980-01-01' WHERE id_entidad = 301");
        var result = consultar("1/operarios/301").andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(fixture("Operaria Ejemplo Uno", "Persona de Prueba 001")))
                .andExpect(jsonPath("$.categoria").value("Oficial 1")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(result).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "codigo", "nombre", "categoria", "fechaAlta", "fechaBaja");
        assertThat(result).doesNotContain("PRIVADO", "email", "nif", "coste", "horasSemanales");
        var parte = consultar("1/partes-mano-obra/" + primerParte()).andExpect(status().isOk())
                .andExpect(jsonPath("$.horasTrabajadas").value("8.00")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(parte).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "obraId", "operarioId", "fecha", "horaInicio", "horaFin", "horasTrabajadas");
    }

    @Test
    void operariosPaginadosConBusquedaLiteralYOrdenEstable() throws Exception {
        jdbc.update("UPDATE API_Operario_Empresa SET id_empresa = 1 WHERE id_operario = 302");
        consultar("1/operarios?size=1&sort=codigo,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].id").value(302));
        consultar("1/operarios?size=1&page=1&sort=codigo,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(301));
        consultar("1/operarios?q=" + fixture("Uno", "001") + "&sort=nombre,asc")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        for (String q : new String[]{"%", "_", "!", "' OR 1=1 --"}) {
            mvc.perform(get("/api/v1/empresas/1/operarios").param("q", q).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        }
    }

    @Test
    void historicoConFiltrosInclusivosYPaginacion() throws Exception {
        consultar("1/partes-mano-obra?desde=2026-09-10&hasta=2026-09-10&obra=1001&operario=301")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].fecha").value("2026-09-10"));
        consultar("1/partes-mano-obra?sort=fecha,desc&size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].fecha").value("2026-09-11"));
        consultar("1/operarios/301/partes?obra=1001&desde=2026-09-11").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        consultar("1/obras/1001/partes?operario=301&hasta=2026-09-10").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        consultar("1/partes-mano-obra?size=1&page=9").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
        consultar("1/obras/1002/partes").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/partes-mano-obra?obra=1003").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/partes-mano-obra?operario=302").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void empateDeFechaSeDesempataPorId() throws Exception {
        jdbc.update("UPDATE Partes_Mano_Obra SET fecha = '2026-09-10' WHERE id_operario = 301");
        consultar("1/partes-mano-obra?sort=fecha,asc&size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(primerParte()));
        consultar("1/partes-mano-obra?sort=fecha,desc&size=1&page=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(primerParte()));
    }

    @Test
    void bajasYNulosConservanElHistoricoSinRecalcularHoras() throws Exception {
        jdbc.update("UPDATE Operarios SET fecha_baja = '2026-09-20', id_categoria = NULL WHERE id_operario = 301");
        jdbc.update("UPDATE Entidades SET activo = FALSE WHERE id_entidad = 301");
        jdbc.update("UPDATE Partes_Mano_Obra SET horas_trabajadas = 7.25, hora_fin = NULL WHERE id_parte = ?", primerParte());
        var ficha = consultar("1/operarios/301").andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaBaja").value("2026-09-20")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(ficha).get("categoria").isNull()).isTrue();
        consultar("1/operarios/301/partes").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        var parte = consultar("1/partes-mano-obra/" + primerParte()).andExpect(status().isOk())
                .andExpect(jsonPath("$.horasTrabajadas").value("7.25")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(parte).get("horaFin").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2/operarios", "1/operarios/302", "1/operarios/302/partes", "1/operarios/999999",
            "1/partes-mano-obra/81003", "1/partes-mano-obra/999999", "1/obras/1003/partes", "1/obras/999999/partes"})
    void empresaOIdsAjenosDevuelven404(String path) throws Exception {
        consultar(path).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void accesoADosEmpresasNoMezclaRecursos() throws Exception {
        asignar(2);
        consultar("2/operarios").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(302));
        var parte = consultar("2/partes-mano-obra/81003").andExpect(status().isOk())
                .andExpect(jsonPath("$.horasTrabajadas").value("2.50")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(parte).get("horaInicio").isNull()).isTrue();
        consultar("2/operarios/301").andExpect(status().isNotFound());
        consultar("1/partes-mano-obra/81003").andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "UPDATE API_Operario_Empresa SET id_empresa = 2 WHERE id_operario = 301",
            "DELETE FROM API_Operario_Empresa WHERE id_operario = 301",
            "UPDATE API_Obra_Empresa SET id_empresa = 2 WHERE id_obra = 1001",
            "DELETE FROM API_Obra_Empresa WHERE id_obra = 1001",
            "DELETE FROM API_Cliente_Empresa WHERE id_cliente = 101"})
    void relacionesSinPertenenciaOIncoherentesOcultanPartes(String sql) throws Exception {
        int parte = primerParte();
        jdbc.update(sql);
        consultar("1/partes-mano-obra").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        consultar("1/partes-mano-obra/" + parte).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERARIOS", "PARTES_MANO_OBRA", "OBRAS"})
    void historicoExigeLosTresPermisos(String permiso) throws Exception {
        retirar(permiso);
        consultar("1/partes-mano-obra").andExpect(status().isNotFound());
        consultar("1/partes-mano-obra/" + primerParte()).andExpect(status().isNotFound());
        consultar("1/operarios/301/partes").andExpect(status().isNotFound());
        consultar("1/obras/1001/partes").andExpect(status().isNotFound());
        consultar("1/operarios/301").andExpect(permiso.equals("OPERARIOS") ? status().isNotFound() : status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"partes-mano-obra?desde=2026-02-30", "partes-mano-obra?hasta=2026-13-01",
            "partes-mano-obra?desde=2026-9-01", "partes-mano-obra?desde=0000-01-01", "partes-mano-obra?desde=",
            "partes-mano-obra?desde=2026-10-01&hasta=2026-09-01", "partes-mano-obra?operario=0",
            "partes-mano-obra?obra=-1", "partes-mano-obra?size=101", "partes-mano-obra?page=-1",
            "partes-mano-obra?sort=coste,asc", "partes-mano-obra?desde=2026-09-10&desde=2026-09-11",
            "operarios?sort=nss,asc", "operarios?activo=true", "operarios/301/partes?operario=302",
            "obras/1001/partes?obra=1003"})
    void parametrosInvalidosDevuelven400(String path) throws Exception {
        consultar("1/" + path).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos").isNotEmpty());
    }

    @Test
    void sesionRevocadaSinCredencialesYEscriturasDenegadas() throws Exception {
        mvc.perform(get("/api/v1/empresas/1/operarios")).andExpect(status().isUnauthorized());
        for (String path : new String[]{"operarios", "partes-mano-obra"}) {
            mvc.perform(post("/api/v1/empresas/1/" + path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        jdbc.update("UPDATE API_Sesiones SET revocada = TRUE WHERE id_usuario = ?", usuario);
        consultar("1/operarios/301").andExpect(status().isUnauthorized());
        consultar("1/partes-mano-obra").andExpect(status().isUnauthorized());
    }
}
