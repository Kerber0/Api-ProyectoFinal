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
class IncidenciasTests extends PostgreSqlIntegrationTest {
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
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-incidencias', ?)", passwords.encode("clave-ficticia-pruebas"));
        usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario = 'test-incidencias'", Integer.class);
        asignar(1);
        token = auth.login(new AuthDtos.Login("test-incidencias", "clave-ficticia-pruebas")).accessToken();
    }
    private void asignar(int empresa) {
        jdbc.update("INSERT INTO API_Accesos_Empresa SELECT ?, ?, id_rol FROM Roles WHERE nombre = 'CONSULTA_RECURSOS_DEMO'", usuario, empresa);
    }
    private ResultActions consultar(String path) throws Exception {
        return mvc.perform(get("/api/v1/empresas/" + path).header("Authorization", "Bearer " + token));
    }

    @Test
    void listadoMinimoYDetalleConDescripcionOriginalSinDatosPersonalesNiAutores() throws Exception {
        jdbc.update("UPDATE Incidencias SET descripcion = ?, usuario_creacion = ? WHERE id_incidencia = 99501",
                "Revisar cierre\n<texto> & ajuste [DEMO].", usuario);
        jdbc.update("UPDATE Entidades SET observaciones = 'PRIVADO', nif_cif = 'PRIVADO-NIF' WHERE id_entidad = 301");
        jdbc.update("UPDATE Operarios SET nss = 'PRIVADO-NSS' WHERE id_operario = 301");
        var body = consultar("1/incidencias?size=1").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("items").get(0).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "obraId", "operarioId", "fecha", "estado", "prioridad");
        assertThat(body).doesNotContain("PRIVADO", "descripcion", "Revisar", "usuario");
        var detalle = consultar("1/incidencias/99501").andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value("Revisar cierre\n<texto> & ajuste [DEMO]."))
                .andExpect(jsonPath("$.estado").value("ABIERTA")).andExpect(jsonPath("$.prioridad").value("ALTA"))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(detalle).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "obraId", "operarioId", "fecha", "estado", "prioridad", "descripcion");
        assertThat(detalle).doesNotContain("PRIVADO", "usuario", "fechaCreacion", "fechaModificacion", "coste", "ausencia");
    }

    @Test
    void relacionesNulasPaginacionYOrdenEstable() throws Exception {
        var detalle = json.readTree(consultar("1/incidencias/99502").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(detalle.get("operarioId").isNull()).isTrue();
        consultar("1/incidencias?size=1&page=1&sort=fecha,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].id").value(99502));
        consultar("1/incidencias?size=1&page=1&sort=fecha,asc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(99502));
        consultar("1/incidencias?sort=id,desc").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(99504));
        consultar("1/incidencias?size=1&page=99").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void filtrosCombinadosFechasInclusivasYTextosSinComodines() throws Exception {
        consultar("1/incidencias?obra=1001&operario=301&estado=ABIERTA&prioridad=ALTA&desde=2026-09-10&hasta=2026-09-10")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(99501));
        consultar("1/incidencias?estado=EN_PROCESO&prioridad=MEDIA").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(99502));
        for (String campo : new String[]{"estado", "prioridad"}) {
            for (String valor : new String[]{"%", "_", "' OR 1=1 --", "INEXISTENTE"}) {
                mvc.perform(get("/api/v1/empresas/1/incidencias").param(campo, valor).header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            }
        }
        for (String filtro : new String[]{"obra=1003", "operario=302", "obra=999999", "operario=999999"}) {
            consultar("1/incidencias?" + filtro).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        }
    }

    @Test
    void navegacionDesdeObraYOperarioConPadresVacios() throws Exception {
        consultar("1/obras/1001/incidencias").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        consultar("1/obras/1001/incidencias?operario=301&prioridad=ALTA").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        consultar("1/operarios/301/incidencias?obra=1001&estado=RESUELTA").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(99504));
        consultar("1/obras/1002/incidencias").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        jdbc.update("UPDATE Incidencias SET id_operario = NULL WHERE id_incidencia IN (99501, 99504)");
        consultar("1/operarios/301/incidencias").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2/incidencias", "2/incidencias/99503", "1/incidencias/99503", "1/incidencias/999999",
            "1/obras/1003/incidencias", "1/operarios/302/incidencias", "1/obras/999999/incidencias", "1/operarios/999999/incidencias"})
    void recursosAjenosYDesconocidosDevuelven404(String ruta) throws Exception {
        consultar(ruta).andExpect(status().isNotFound());
    }

    @Test
    void accesoADosEmpresasMantieneSeparadosConteosYDetalles() throws Exception {
        asignar(2);
        consultar("2/incidencias").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(99503));
        consultar("2/incidencias/99503").andExpect(status().isOk());
        consultar("2/incidencias/99501").andExpect(status().isNotFound());
        consultar("1/incidencias/99503").andExpect(status().isNotFound());
        consultar("1/incidencias").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"incidencia-sin-asignar", "incidencia-ajena", "obra-ajena", "obra-sin-asignar",
            "cliente-sin-asignar", "operario-ajeno", "operario-sin-asignar", "operario-no-persona"})
    void incoherenciasExcluyenDetallesListadosYConteos(String caso) throws Exception {
        switch (caso) {
            case "incidencia-sin-asignar" -> jdbc.update("DELETE FROM API_Incidencia_Empresa WHERE id_incidencia = 99501");
            case "incidencia-ajena" -> jdbc.update("UPDATE API_Incidencia_Empresa SET id_empresa = 2 WHERE id_incidencia = 99501");
            case "obra-ajena" -> jdbc.update("UPDATE Incidencias SET id_obra = 1003 WHERE id_incidencia = 99501");
            case "obra-sin-asignar" -> jdbc.update("DELETE FROM API_Obra_Empresa WHERE id_obra = 1001");
            case "cliente-sin-asignar" -> jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_cliente = 101");
            case "operario-ajeno" -> jdbc.update("UPDATE Incidencias SET id_operario = 302 WHERE id_incidencia = 99501");
            case "operario-sin-asignar" -> jdbc.update("DELETE FROM API_Operario_Empresa WHERE id_operario = 301");
            case "operario-no-persona" -> jdbc.update("UPDATE Entidades SET tipo_entidad = 'EMPRESA', razon_social = 'Empresa de prueba [DEMO]', nombre = NULL, apellidos = NULL WHERE id_entidad = 301");
        }
        consultar("1/incidencias/99501").andExpect(status().isNotFound());
        consultar("1/incidencias?estado=ABIERTA").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        asignar(2);
        consultar("2/incidencias/99501").andExpect(status().isNotFound());
        if (caso.startsWith("operario")) {
            consultar("1/incidencias/99502").andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"INCIDENCIAS", "OBRAS", "CLIENTES", "OPERARIOS"})
    void exigeTodosLosPermisosInclusoSinOperarioYAntesDeValidar(String permiso) throws Exception {
        jdbc.update("DELETE FROM Rol_Permiso WHERE id_rol = (SELECT id_rol FROM Roles WHERE nombre = 'CONSULTA_RECURSOS_DEMO') AND id_permiso = (SELECT id_permiso FROM Permisos WHERE nombre = ?)", permiso);
        for (String ruta : new String[]{"incidencias?size=0", "incidencias/99501", "incidencias/99502",
                "obras/1001/incidencias?size=0", "operarios/301/incidencias?size=0"}) {
            consultar("1/" + ruta).andExpect(status().isNotFound());
        }
    }

    @Test
    void permisoEnOtraEmpresaNoAutorizaLaEmpresaSolicitada() throws Exception {
        asignar(2);
        jdbc.update("UPDATE API_Accesos_Empresa SET id_rol = (SELECT id_rol FROM Roles WHERE nombre = 'ADMINISTRADOR') WHERE id_usuario = ? AND id_empresa = 1", usuario);
        // Los roles globales de ejemplo no tienen el permiso DEMO nuevo.
        consultar("1/incidencias").andExpect(status().isNotFound());
        consultar("2/incidencias").andExpect(status().isOk());
    }

    @Test
    void bajasYEstadosPersonalizadosSeConservanSinDeducirUnCicloDeTrabajo() throws Exception {
        jdbc.update("UPDATE Operarios SET fecha_baja = '2026-09-01' WHERE id_operario = 301");
        jdbc.update("UPDATE Entidades SET activo = FALSE WHERE id_entidad IN (101, 301)");
        jdbc.update("UPDATE Estados_Incidencia SET activo = FALSE, descripcion = 'REVISION_MANUAL' WHERE descripcion = 'ABIERTA'");
        jdbc.update("UPDATE Prioridades SET activo = FALSE WHERE descripcion = 'ALTA'");
        consultar("1/incidencias/99501").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REVISION_MANUAL"));
        consultar("1/incidencias?estado=REVISION_MANUAL&prioridad=ALTA").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        consultar("1/operarios/301/incidencias").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"incidencias?page=-1", "incidencias?page=100001", "incidencias?size=0", "incidencias?size=101",
            "incidencias?sort=prioridad,asc", "incidencias?sort=fecha,ASC", "incidencias?obra=0", "incidencias?operario=abc",
            "incidencias?operario=2147483648", "incidencias?desde=2026-02-30", "incidencias?desde=0999-01-01",
            "incidencias?desde=2026-10-01&hasta=2026-09-01", "incidencias?prioridad=ALTA&prioridad=BAJA",
            "incidencias?estado=ABIERTA&estado=CERRADA", "incidencias?obra=1001&obra=1002", "incidencias?q=texto",
            "incidencias?desconocido=1", "obras/1001/incidencias?obra=1001", "operarios/301/incidencias?operario=301"})
    void filtrosInvalidosDevuelven400(String ruta) throws Exception {
        consultar("1/" + ruta).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void limitaLongitudesYValidaElPadreAntesDeLosFiltros() throws Exception {
        for (String campo : new String[]{"estado", "prioridad"}) {
            mvc.perform(get("/api/v1/empresas/1/incidencias").param(campo, "x".repeat(301))
                    .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        }
        consultar("1/obras/1003/incidencias?size=0").andExpect(status().isNotFound());
        consultar("1/operarios/302/incidencias?size=0").andExpect(status().isNotFound());
    }

    @Test
    void autenticacionEscriturasDenegadasYEmpresaInactiva() throws Exception {
        int antes = jdbc.queryForObject("SELECT COUNT(*) FROM Incidencias", Integer.class);
        for (String ruta : new String[]{"incidencias", "incidencias/99501", "obras/1001/incidencias", "operarios/301/incidencias"}) {
            String path = "/api/v1/empresas/1/" + ruta;
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(post(path).contentType("application/json").content("{}").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(put(path).contentType("application/json").content("{}").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(delete(path).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM Incidencias", Integer.class)).isEqualTo(antes);
        jdbc.update("UPDATE API_Empresas SET activo = FALSE WHERE id_empresa = 1");
        consultar("1/incidencias?size=0").andExpect(status().isNotFound());
        consultar("1/incidencias/99501").andExpect(status().isNotFound());
    }
}
