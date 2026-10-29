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
class CalendarioTests extends PostgreSqlIntegrationTest {
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
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-calendario', ?)", passwords.encode("clave-ficticia-pruebas"));
        usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario = 'test-calendario'", Integer.class);
        asignar(1);
        token = auth.login(new AuthDtos.Login("test-calendario", "clave-ficticia-pruebas")).accessToken();
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

    @Test
    void camposMinimosDescripcionSoloEnDetalleYHorasSinZonaInventada() throws Exception {
        var eventos = consultar("1/eventos?size=1").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(eventos).get("items").get(0).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "titulo", "fechaInicio", "fechaFin", "tipo", "obraId");
        assertThat(eventos).doesNotContain("descripcion", "usuario", "color", "operarioId");
        consultar("1/eventos/99601").andExpect(status().isOk()).andExpect(jsonPath("$.fechaInicio").value("2026-09-10T08:00:00"))
                .andExpect(jsonPath("$.fechaFin").value("2026-09-12T17:00:00"))
                .andExpect(jsonPath("$.descripcion").value(fixture(
                        "Comprobar herrajes antes del montaje [DEMO].", "Dato sintético 060")));
        var tareas = consultar("1/tareas?size=1").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(tareas).get("items").get(0).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "titulo", "fechaLimite", "fechaFinalizacion", "estado", "prioridad", "obraId", "operarioId");
        assertThat(tareas).doesNotContain("descripcion", "usuario", "coste", "email");
        jdbc.update("UPDATE Tareas SET descripcion = ? WHERE id_tarea = 99701", "Texto\n<original> [DEMO]");
        consultar("1/tareas/99701").andExpect(status().isOk()).andExpect(jsonPath("$.descripcion").value("Texto\n<original> [DEMO]"));
    }

    @Test
    void nulosSeConservanYNoInventanAsignacionesNiFechas() throws Exception {
        var evento = json.readTree(consultar("1/eventos/99602").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (String field : new String[]{"obraId", "fechaFin", "descripcion"}) { assertThat(evento.get(field).isNull()).isTrue(); }
        var tarea = json.readTree(consultar("1/tareas/99702").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (String field : new String[]{"obraId", "operarioId", "fechaLimite", "fechaFinalizacion", "descripcion"}) { assertThat(tarea.get(field).isNull()).isTrue(); }
        consultar("1/tareas/99704").andExpect(status().isOk()).andExpect(jsonPath("$.fechaFinalizacion").value("2026-09-09"));
    }

    @Test
    void eventosIncluyenLosQueCruzanElPeriodoYElFinNuloSoloUsaElInicio() throws Exception {
        consultar("1/eventos?desde=2026-09-11&hasta=2026-09-11").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        consultar("1/eventos?desde=2026-09-12&hasta=2026-09-12").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(99601));
        consultar("1/eventos?desde=2026-09-13").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        consultar("1/eventos?hasta=2026-09-09").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        jdbc.update("UPDATE Eventos SET fecha_inicio = '2026-09-11 00:00:00', fecha_fin = '2026-09-11 00:00:00' WHERE id_evento = 99601");
        consultar("1/eventos?desde=2026-09-11&hasta=2026-09-11").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        jdbc.update("UPDATE Eventos SET fecha_inicio = '9999-12-31 23:59:59', fecha_fin = NULL WHERE id_evento = 99602");
        consultar("1/eventos?desde=9999-12-31&hasta=9999-12-31").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(99602));
    }

    @Test
    void ultimoMicrosegundoDelDiaSeIncluyePeroMedianocheSiguienteNo() throws Exception {
        jdbc.update("UPDATE Eventos SET fecha_inicio='2026-09-13 23:59:59.999999', fecha_fin=NULL WHERE id_evento=99602");
        consultar("1/eventos?desde=2026-09-13&hasta=2026-09-13").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(99602));
        jdbc.update("UPDATE Eventos SET fecha_inicio='2026-09-14 00:00:00' WHERE id_evento=99602");
        consultar("1/eventos?desde=2026-09-13&hasta=2026-09-13").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void tareasFiltranFechaLimiteSinConfundirFinalizacionYConservanSinFechaSinFiltro() throws Exception {
        consultar("1/tareas").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        consultar("1/tareas?desde=2026-09-10&hasta=2026-09-10").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        consultar("1/tareas?desde=2026-09-09&hasta=2026-09-09").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/tareas?estado=FINALIZADA&prioridad=BAJA&obra=1001&operario=301").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(99704));
        consultar("1/eventos?tipo=REUNION&obra=1001").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(99604));
    }

    @Test
    void paginacionDesempateYNulosAlOrdenarFecha() throws Exception {
        consultar("1/eventos?sort=fechaInicio,desc&size=1&page=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items[0].id").value(99602));
        consultar("1/tareas?sort=fechaLimite,asc&size=1").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(99702));
        consultar("1/tareas?sort=fechaLimite,desc&size=1").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(99704));
        consultar("1/eventos?page=99").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/tareas?page=99").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventos", "tareas"})
    void busquedaSoloEnTituloLiteralYFiltrosAjenosNoCoinciden(String ruta) throws Exception {
        for (String q : new String[]{"%", "_", "!", "' OR 1=1 --"}) {
            mvc.perform(get("/api/v1/empresas/1/" + ruta).param("q", q).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        }
        consultar("1/" + ruta + "?obra=1003").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        String titulo = ruta.equals("tareas") ? "068" : "061";
        String soloTarea = ruta.equals("tareas") ? "066" : "060";
        consultar("1/" + ruta + "?q=" + titulo).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        consultar("1/" + ruta + "?q=" + soloTarea).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(ruta.equals("tareas") ? 1 : 0));
    }

    @Test
    void padresAccesiblesConRecursosYVacios() throws Exception {
        consultar("1/obras/1001/eventos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        consultar("1/obras/1001/tareas").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        consultar("1/operarios/301/tareas?obra=1001").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        for (String ruta : new String[]{"eventos", "tareas"}) {
            consultar("1/obras/1002/" + ruta).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        }
        jdbc.update("UPDATE Tareas SET id_operario = NULL WHERE id_tarea IN (99701,99704)");
        consultar("1/operarios/301/tareas").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PERSONAL", "VACACIONES"})
    void eventosNoOperativosNoSePublicanPorDetalleFiltroNiConteo(String tipo) throws Exception {
        jdbc.update("UPDATE Eventos SET tipo_evento = ?, titulo = 'TEXTO-PRIVADO' WHERE id_evento = 99601", tipo);
        consultar("1/eventos/99601").andExpect(status().isNotFound());
        consultar("1/eventos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        consultar("1/eventos?tipo=" + tipo).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/obras/1001/eventos?q=TEXTO-PRIVADO").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void intervalosInvertidosSeExcluyenSinCorregirElOrigen() throws Exception {
        jdbc.update("UPDATE Eventos SET fecha_fin = '2026-09-09 17:00:00' WHERE id_evento = 99601");
        consultar("1/eventos/99601").andExpect(status().isNotFound());
        consultar("1/eventos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        consultar("1/obras/1001/eventos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2/eventos", "2/tareas", "1/eventos/99603", "1/tareas/99703", "1/eventos/999999", "1/tareas/999999",
            "1/obras/1003/eventos", "1/obras/1003/tareas", "1/operarios/302/tareas", "1/obras/999999/eventos", "1/operarios/999999/tareas"})
    void recursosAjenosODesconocidosDevuelven404(String ruta) throws Exception { consultar(ruta).andExpect(status().isNotFound()); }

    @Test
    void dosEmpresasNoMezclanRegistros() throws Exception {
        asignar(2);
        consultar("2/eventos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        consultar("2/tareas").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(99703));
        consultar("2/eventos/99601").andExpect(status().isNotFound());
        consultar("2/tareas/99701").andExpect(status().isNotFound());
        consultar("1/eventos/99603").andExpect(status().isNotFound());
        consultar("1/tareas/99703").andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin-asignar", "empresa-ajena", "obra-ajena", "obra-sin-asignar", "cliente-sin-asignar"})
    void pertenenciasIncoherentesExcluyenTodoElRecurso(String caso) throws Exception {
        switch (caso) {
            case "sin-asignar" -> {
                jdbc.update("DELETE FROM API_Evento_Empresa WHERE id_evento = 99601");
                jdbc.update("DELETE FROM API_Tarea_Empresa WHERE id_tarea = 99701");
            }
            case "empresa-ajena" -> {
                jdbc.update("UPDATE API_Evento_Empresa SET id_empresa = 2 WHERE id_evento = 99601");
                jdbc.update("UPDATE API_Tarea_Empresa SET id_empresa = 2 WHERE id_tarea = 99701");
            }
            case "obra-ajena" -> {
                jdbc.update("UPDATE Eventos SET id_obra = 1003 WHERE id_evento = 99601");
                jdbc.update("UPDATE Tareas SET id_obra = 1003 WHERE id_tarea = 99701");
            }
            case "obra-sin-asignar" -> jdbc.update("DELETE FROM API_Obra_Empresa WHERE id_obra = 1001");
            case "cliente-sin-asignar" -> jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_cliente = 101");
        }
        consultar("1/eventos/99601").andExpect(status().isNotFound());
        consultar("1/tareas/99701").andExpect(status().isNotFound());
        consultar("1/eventos?q=Montaje").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        consultar("1/tareas?q=Preparar").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        consultar("1/eventos/99602").andExpect(status().isOk());
        consultar("1/tareas/99702").andExpect(status().isOk());
        asignar(2);
        consultar("2/eventos/99601").andExpect(status().isNotFound());
        consultar("2/tareas/99701").andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ajeno", "sin-asignar", "no-persona"})
    void operarioIncoherenteOcultaTareaPeroNoTareaSinOperario(String caso) throws Exception {
        switch (caso) {
            case "ajeno" -> jdbc.update("UPDATE Tareas SET id_operario = 302 WHERE id_tarea = 99701");
            case "sin-asignar" -> jdbc.update("DELETE FROM API_Operario_Empresa WHERE id_operario = 301");
            case "no-persona" -> jdbc.update("UPDATE Entidades SET tipo_entidad = 'EMPRESA', nombre = NULL, apellidos = NULL, razon_social = 'Empresa [DEMO]' WHERE id_entidad = 301");
        }
        consultar("1/tareas/99701").andExpect(status().isNotFound());
        consultar("1/tareas?q=Preparar").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/tareas/99702").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EVENTOS", "TAREAS", "OBRAS", "CLIENTES", "OPERARIOS"})
    void permisosDeCadaFuenteTambienConPadresNulosAntesDeValidar(String permiso) throws Exception {
        retirar(permiso);
        boolean eventos = !permiso.equals("TAREAS") && !permiso.equals("OPERARIOS");
        boolean tareas = !permiso.equals("EVENTOS");
        for (String ruta : new String[]{"eventos?size=0", "eventos/99602", "obras/1001/eventos?size=0"}) {
            if (eventos) { consultar("1/" + ruta).andExpect(status().isNotFound()); }
        }
        for (String ruta : new String[]{"tareas?size=0", "tareas/99702", "obras/1001/tareas?size=0", "operarios/301/tareas?size=0"}) {
            if (tareas) { consultar("1/" + ruta).andExpect(status().isNotFound()); }
        }
        if (!eventos) { consultar("1/eventos").andExpect(status().isOk()); }
        if (!tareas) { consultar("1/tareas").andExpect(status().isOk()); }
    }

    @Test
    void bajasNoBorranHistoricoNiSeDeducenTransiciones() throws Exception {
        jdbc.update("UPDATE Entidades SET activo = FALSE WHERE id_entidad IN (101,301)");
        jdbc.update("UPDATE Operarios SET fecha_baja = '2026-09-01' WHERE id_operario = 301");
        jdbc.update("UPDATE Estados_Tarea SET activo = FALSE, descripcion = 'REVISION' WHERE descripcion = 'PENDIENTE'");
        jdbc.update("UPDATE Prioridades SET activo = FALSE WHERE descripcion = 'ALTA'");
        consultar("1/tareas/99701").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REVISION"));
        consultar("1/operarios/301/tareas").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        consultar("1/eventos/99601").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"eventos?size=0", "tareas?size=101", "eventos?page=-1", "tareas?page=100001",
            "eventos?desde=2026-02-30", "tareas?desde=2026-10-01&hasta=2026-09-01", "eventos?hasta=10000-01-01",
            "eventos?tipo=OBRA&tipo=REUNION", "tareas?prioridad=ALTA&prioridad=BAJA", "tareas?estado=PENDIENTE&estado=FINALIZADA",
            "eventos?operario=301", "eventos?estado=ABIERTA", "tareas?tipo=OBRA", "eventos?obra=0", "tareas?operario=abc",
            "eventos?sort=fecha,asc", "tareas?sort=fechaLimite,ASC", "obras/1001/eventos?obra=1001",
            "obras/1001/tareas?obra=1001", "operarios/301/tareas?operario=301"})
    void filtrosInvalidos(String ruta) throws Exception {
        consultar("1/" + ruta).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void longitudesYAutorizacionDePadresAntesDeFiltros() throws Exception {
        for (String[] caso : new String[][]{{"eventos","tipo","31"},{"tareas","prioridad","301"},{"tareas","q","101"}}) {
            mvc.perform(get("/api/v1/empresas/1/" + caso[0]).param(caso[1], "x".repeat(Integer.parseInt(caso[2])))
                    .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        }
        consultar("1/obras/1003/eventos?size=0").andExpect(status().isNotFound());
        consultar("1/operarios/302/tareas?size=0").andExpect(status().isNotFound());
    }

    @Test
    void autenticacionEscriturasDenegadasYEmpresaInactiva() throws Exception {
        for (String ruta : new String[]{"eventos", "eventos/99601", "obras/1001/eventos", "tareas", "tareas/99701", "obras/1001/tareas", "operarios/301/tareas"}) {
            String path = "/api/v1/empresas/1/" + ruta;
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(post(path).contentType("application/json").content("{}").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(put(path).contentType("application/json").content("{}").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
            mvc.perform(delete(path).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        jdbc.update("UPDATE API_Empresas SET activo = FALSE WHERE id_empresa = 1");
        consultar("1/eventos?size=0").andExpect(status().isNotFound());
        consultar("1/tareas/99701").andExpect(status().isNotFound());
    }
}
