package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.operativa.OperativaDtos.EventoEntrada;
import com.empresa.api.operativa.OperativaDtos.IncidenciaEntrada;
import com.empresa.api.operativa.OperativaDtos.TareaEntrada;
import com.empresa.api.operativa.OperativaService;
import com.empresa.api.security.AuthDtos;
import com.empresa.api.security.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class GestionOperativaTests extends PostgreSqlIntegrationTest {
    @Autowired OperativaService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired jakarta.persistence.EntityManager entities;

    int admin;
    int estadoIncidencia;
    int estadoTarea;
    int prioridad;
    String token;
    static final String CLAVE = "clave-gestion-operativa";

    @BeforeEach
    void preparar() {
        admin = usuario("test-gestion-operativa-admin", "ADMIN", null);
        token = auth.login(new AuthDtos.Login("test-gestion-operativa-admin", CLAVE)).accessToken();
        estadoIncidencia = jdbc.queryForObject("SELECT id_estado FROM Estados_Incidencia WHERE activo ORDER BY id_estado LIMIT 1", Integer.class);
        estadoTarea = jdbc.queryForObject("SELECT id_estado FROM Estados_Tarea WHERE activo ORDER BY id_estado LIMIT 1", Integer.class);
        prioridad = jdbc.queryForObject("SELECT id_prioridad FROM Prioridades WHERE activo ORDER BY id_prioridad LIMIT 1", Integer.class);
    }

    int usuario(String nombre, String rol, Integer empresa) {
        int id = jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,?) RETURNING id_usuario",
                Integer.class, nombre, passwords.encode(CLAVE));
        if (empresa == null) {
            jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre=?", id, rol);
        } else {
            jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) SELECT ?,?,id_rol FROM Roles WHERE nombre=?", id, empresa, rol);
        }
        return id;
    }

    IncidenciaEntrada incidencia(String descripcion) {
        return new IncidenciaEntrada(1001, 301, LocalDate.of(2026, 10, 1), descripcion, estadoIncidencia, prioridad);
    }

    TareaEntrada tarea(String titulo) {
        return new TareaEntrada(titulo, "Descripción privada", estadoTarea, prioridad,
                LocalDate.of(2026, 10, 20), null, 1001, 301);
    }

    EventoEntrada evento(String titulo, LocalDateTime fin) {
        return new EventoEntrada(titulo, "Descripción privada", LocalDateTime.of(2026, 10, 2, 8, 0),
                fin, "#123456", "OBRA", 1001);
    }

    void error(Runnable accion, int estado) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.status()).isEqualTo(estado));
    }

    @Test
    void incidenciaSeCreaActualizaYPermaneceEnLaConsultaAnterior() throws Exception {
        var creada = service.incidencia(admin, 1, null, incidencia("Incidencia privada"));
        var actualizada = service.incidencia(admin, 1, creada.id(), incidencia("Incidencia actualizada"));

        assertThat(actualizada.descripcion()).isEqualTo("Incidencia actualizada");
        mvc.perform(get("/api/v1/empresas/1/incidencias/" + creada.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.descripcion").value("Incidencia actualizada"));
    }

    @Test
    void tareaSeCreaActualizaYPermaneceEnLaConsultaAnterior() throws Exception {
        var creada = service.tarea(admin, 1, null, tarea("Tarea privada"));
        var actualizada = service.tarea(admin, 1, creada.id(), tarea("Tarea actualizada"));

        assertThat(actualizada.titulo()).isEqualTo("Tarea actualizada");
        mvc.perform(get("/api/v1/empresas/1/tareas/" + creada.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.titulo").value("Tarea actualizada"));
    }

    @Test
    void eventoSeCreaActualizaYPermaneceEnLaConsultaAnterior() throws Exception {
        var fin = LocalDateTime.of(2026, 10, 2, 12, 0);
        var creado = service.evento(admin, 1, null, evento("Evento privado", fin));
        var actualizado = service.evento(admin, 1, creado.id(), evento("Evento actualizado", fin));

        assertThat(actualizado.titulo()).isEqualTo("Evento actualizado");
        mvc.perform(get("/api/v1/empresas/1/eventos/" + creado.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.titulo").value("Evento actualizado"));
    }

    @Test
    void referenciasAjenasInexistentesOInactivasNoSeEnumeran() {
        error(() -> service.incidencia(admin, 1, null,
                new IncidenciaEntrada(1003, null, LocalDate.now(), "Ajena", estadoIncidencia, prioridad)), 404);
        error(() -> service.tarea(admin, 1, 999999, tarea("Inexistente")), 404);
        error(() -> service.evento(admin, 1, null,
                new EventoEntrada("Ajeno", null, LocalDateTime.now(), null, null, "OBRA", 1003)), 404);
        error(() -> service.incidencia(admin, 1, null,
                new IncidenciaEntrada(1001, null, LocalDate.now(), "Catálogo", 999999, prioridad)), 404);
        jdbc.update("UPDATE Prioridades SET activo=false WHERE id_prioridad=?", prioridad);
        entities.clear();
        error(() -> service.tarea(admin, 1, null, tarea("Prioridad inactiva")), 404);
    }

    @Test
    void eventoRechazaFinAnteriorAlInicio() {
        error(() -> service.evento(admin, 1, null,
                evento("Fechas inválidas", LocalDateTime.of(2026, 10, 2, 7, 59))), 400);
    }

    @Test
    void lectorNoPuedeCrearPorHttpYLaValidacionEsEstricta() throws Exception {
        usuario("test-gestion-operativa-lector", "CONSULTA_RECURSOS_DEMO", 1);
        String lector = auth.login(new AuthDtos.Login("test-gestion-operativa-lector", CLAVE)).accessToken();
        mvc.perform(post("/api/v1/empresas/1/gestion/incidencias")
                        .header("Authorization", "Bearer " + lector).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(incidencia("Sin escritura"))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/empresas/1/gestion/eventos")
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\" \",\"fechaInicio\":\"2026-10-02T08:00:00\",\"tipoEvento\":\"OBRA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditoriaConservaReferenciasSinCopiarTextoPrivado() {
        var creada = service.incidencia(admin, 1, null, incidencia("Descripción extremadamente privada"));
        String cambios = jdbc.queryForObject(
                "SELECT cambios::text FROM API_Gestion_Auditoria WHERE entidad='INCIDENCIA' AND entidad_id=? ORDER BY id DESC LIMIT 1",
                String.class, creada.id());
        assertThat(cambios).contains("obra", "estado", "prioridad")
                .doesNotContain("Descripción extremadamente privada");
    }
}
