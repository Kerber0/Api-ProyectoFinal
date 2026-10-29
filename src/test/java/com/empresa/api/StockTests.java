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
class StockTests extends PostgreSqlIntegrationTest {
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
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-stock', ?)", passwords.encode("clave-ficticia-pruebas"));
        usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario = 'test-stock'", Integer.class);
        asignar(1);
        token = auth.login(new AuthDtos.Login("test-stock", "clave-ficticia-pruebas")).accessToken();
    }
    private void asignar(int empresa) {
        jdbc.update("INSERT INTO API_Accesos_Empresa SELECT ?, ?, id_rol FROM Roles WHERE nombre = 'CONSULTA_RECURSOS_DEMO'", usuario, empresa);
    }
    private ResultActions consultar(String path) throws Exception {
        return mvc.perform(get("/api/v1/empresas/" + path).header("Authorization", "Bearer " + token));
    }
    private void saldo(int almacen, String referencia, String cantidad) throws Exception {
        consultar("1/almacenes/" + almacen + "/stock?q=" + referencia).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].cantidad").value(cantidad))
                .andExpect(jsonPath("$.items[0].calculable").value(true))
                .andExpect(jsonPath("$.items[0].movimientosPendientes").value(0));
    }
    private void pendiente(int almacen) throws Exception {
        var body = consultar("1/almacenes/" + almacen + "/stock?q=DEMO-ART-001").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].calculable").value(false))
                .andExpect(jsonPath("$.items[0].movimientosPendientes").value(1)).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("items").get(0).get("cantidad").isNull()).isTrue();
    }

    @Test
    void fichasMinimasSinPreciosNiDatosDelProveedor() throws Exception {
        var articulo = consultar("1/articulos/DEMO-ART-001").andExpect(status().isOk())
                .andExpect(jsonPath("$.unidad").value("ud")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(articulo).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("referencia", "descripcion", "marca", "modelo", "unidad", "activo");
        var almacen = consultar("1/almacenes/101").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(almacen).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "codigo", "descripcion", "activo");
        jdbc.update("UPDATE Movimientos_Stock SET origen = 'PRIVADO', destino = 'PRIVADO', observaciones = 'PRIVADO', documento = 'PRIVADO' WHERE id_movimiento = 97001");
        var movimiento = consultar("1/movimientos-stock/97001").andExpect(status().isOk())
                .andExpect(jsonPath("$.almacenDestinoId").value(102))
                .andExpect(jsonPath("$.efectoOrigen").value("-2.00")).andReturn().getResponse().getContentAsString();
        assertThat(movimiento).doesNotContain("PRIVADO", "precio", "usuario", "documento");
        assertThat(json.readTree(movimiento).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "referencia", "almacenId", "almacenDestinoId", "fecha", "tipo", "cantidad", "efectoOrigen", "calculable");
    }

    @Test
    void saldosPorAlmacenIncluyenAjustesYAmbosLadosDelTraspasoSinDuplicados() throws Exception {
        saldo(101, "DEMO-ART-001", "6.50");
        saldo(102, "DEMO-ART-001", "2.00");
        saldo(101, "DEMO-ART-002", "4.00");
        saldo(102, "DEMO-ART-002", "0.00");
        saldo(101, "DEMO-ART-004", "0.00");
        consultar("1/movimientos-stock").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6));
        consultar("1/almacenes/102/movimientos").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(97001));
        consultar("1/almacenes/101/movimientos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void saldoConciliaConElHistoricoDeCadaAlmacen() throws Exception {
        for (int almacen : new int[]{101, 102}) {
            var movimientos = json.readTree(consultar("1/almacenes/" + almacen + "/movimientos")
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("items");
            var sumas = new java.util.HashMap<String, java.math.BigDecimal>();
            for (var m : movimientos) {
                var efecto = new java.math.BigDecimal(m.get("efectoOrigen").asText());
                if (m.get("almacenId").asInt() != almacen) { efecto = efecto.negate(); }
                sumas.merge(m.get("referencia").asText(), efecto, java.math.BigDecimal::add);
            }
            var existencias = json.readTree(consultar("1/almacenes/" + almacen + "/stock")
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("items");
            for (var e : existencias) {
                assertThat(new java.math.BigDecimal(e.get("cantidad").asText()))
                        .isEqualByComparingTo(sumas.getOrDefault(e.get("referencia").asText(), java.math.BigDecimal.ZERO));
            }
        }
    }

    @Test
    void paginacionBusquedaLiteralYDesempates() throws Exception {
        consultar("1/articulos?size=1&sort=referencia,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].referencia").value("DEMO-ART-004"));
        jdbc.update("UPDATE Articulos SET descripcion = 'Igual' WHERE referencia IN ('DEMO-ART-001', 'DEMO-ART-002')");
        consultar("1/articulos?size=1&page=1&sort=descripcion,asc&q=Igual").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].referencia").value("DEMO-ART-002"));
        consultar("1/almacenes?size=1&page=1&sort=codigo,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(101));
        consultar("1/almacenes/101/stock?size=1&page=1&sort=referencia,asc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].referencia").value("DEMO-ART-002"));
        consultar("1/articulos?size=1&page=99").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
        for (String ruta : new String[]{"articulos", "almacenes", "almacenes/101/stock"}) {
            for (String q : new String[]{"%", "_", "!", "' OR 1=1 --"}) {
                mvc.perform(get("/api/v1/empresas/1/" + ruta).param("q", q).header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            }
        }
    }

    @Test
    void fechasInclusivasHastaUltimoSegundoYOrdenEstable() throws Exception {
        consultar("1/movimientos-stock?desde=2026-09-13&hasta=2026-09-13").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(97002));
        consultar("1/movimientos-stock?desde=2026-09-14").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/movimientos-stock?hasta=9999-12-31&sort=fecha,desc&size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(97002));
        consultar("1/movimientos-stock?hasta=2026-09-01&sort=fecha,asc&size=1&page=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].referencia").value("DEMO-ART-002"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2/articulos", "1/articulos/DEMO-ART-003", "1/articulos/INEXISTENTE", "1/almacenes/201",
            "1/almacenes/999999", "1/almacenes/201/stock", "1/almacenes/201/movimientos", "1/movimientos-stock/97003", "1/movimientos-stock/999999"})
    void recursosAjenosOInexistentesDevuelven404(String path) throws Exception {
        consultar(path).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void dosEmpresasNoMezclanCatalogoMovimientosNiSaldos() throws Exception {
        asignar(2);
        consultar("2/articulos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        consultar("2/almacenes/201/stock").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].cantidad").value("7.00"));
        consultar("2/movimientos-stock").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        consultar("2/articulos/DEMO-ART-001").andExpect(status().isNotFound());
        consultar("1/almacenes/101/stock?q=DEMO-ART-003").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void pertenenciaIncoherenteONoAsignadaNuncaFiltraMovimientos() throws Exception {
        jdbc.update("UPDATE Movimientos_Stock SET id_almacen = 201 WHERE id_movimiento = 97002");
        consultar("1/movimientos-stock/97002").andExpect(status().isNotFound());
        saldo(101, "DEMO-ART-001", "7.00");
        jdbc.update("DELETE FROM API_Articulo_Empresa WHERE referencia_articulo = 'DEMO-ART-001'");
        consultar("1/articulos/DEMO-ART-001").andExpect(status().isNotFound());
        consultar("1/movimientos-stock/97001").andExpect(status().isNotFound());
        consultar("1/almacenes/101/stock").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        jdbc.update("DELETE FROM API_Almacen_Empresa WHERE id_almacen = 101");
        consultar("1/almacenes/101/stock").andExpect(status().isNotFound());
        consultar("1/movimientos-stock").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ausente", "ajeno", "mismo", "sin-pertenencia"})
    void destinoNoResueltoNoSeInfiereNiSeOcultaComoCero(String caso) throws Exception {
        switch (caso) {
            case "ausente" -> jdbc.update("DELETE FROM API_Traspaso_Stock WHERE id_movimiento = 97001");
            case "ajeno" -> jdbc.update("UPDATE API_Traspaso_Stock SET id_almacen_destino = 201 WHERE id_movimiento = 97001");
            case "mismo" -> jdbc.update("UPDATE API_Traspaso_Stock SET id_almacen_destino = 101 WHERE id_movimiento = 97001");
            case "sin-pertenencia" -> jdbc.update("DELETE FROM API_Almacen_Empresa WHERE id_almacen = 102");
        }
        pendiente(101);
        if (!caso.equals("sin-pertenencia")) { pendiente(102); }
        var body = consultar("1/movimientos-stock/97001").andExpect(status().isOk())
                .andExpect(jsonPath("$.calculable").value(false)).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("almacenDestinoId").isNull()).isTrue();
        assertThat(json.readTree(body).get("efectoOrigen").isNull()).isTrue();
        saldo(101, "DEMO-ART-002", "4.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENTRADA", "SALIDA", "TRASPASO"})
    void cantidadesNegativasIncompatiblesDejanSaldoPendiente(String tipo) throws Exception {
        jdbc.update("UPDATE Movimientos_Stock SET tipo_movimiento = ?, cantidad = -2 WHERE id_movimiento = 97001", tipo);
        pendiente(101);
        if (tipo.equals("TRASPASO")) { pendiente(102); }
        else { saldo(102, "DEMO-ART-001", "0.00"); }
    }

    @Test
    void bajasYNulosNoBorranHistoricoYSaldoNegativoSeConserva() throws Exception {
        jdbc.update("UPDATE Articulos SET activo = FALSE, id_unidad = NULL WHERE referencia = 'DEMO-ART-001'");
        jdbc.update("UPDATE Almacenes SET activo = FALSE WHERE id_almacen = 101");
        jdbc.update("UPDATE Movimientos_Stock SET cantidad = -20 WHERE id_movimiento = 97002");
        saldo(101, "DEMO-ART-001", "-13.00");
        consultar("1/articulos/DEMO-ART-001").andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        consultar("1/almacenes/101").andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        consultar("1/movimientos-stock").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARTICULOS", "ALMACENES", "STOCK"})
    void stockExigeTodosLosPermisosEnLaEmpresaActual(String permiso) throws Exception {
        jdbc.update("DELETE FROM Rol_Permiso WHERE id_rol = (SELECT id_rol FROM Roles WHERE nombre = 'CONSULTA_RECURSOS_DEMO') AND id_permiso = (SELECT id_permiso FROM Permisos WHERE nombre = ?)", permiso);
        for (String path : new String[]{"almacenes/101/stock", "almacenes/101/movimientos", "movimientos-stock", "movimientos-stock/97001"}) {
            consultar("1/" + path).andExpect(status().isNotFound());
        }
        if (permiso.equals("ARTICULOS")) { consultar("1/articulos").andExpect(status().isNotFound()); }
        if (permiso.equals("ALMACENES")) { consultar("1/almacenes").andExpect(status().isNotFound()); }
        if (permiso.equals("STOCK")) {
            consultar("1/articulos").andExpect(status().isOk());
            consultar("1/almacenes").andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"articulos?size=101", "articulos?size=0", "articulos?page=-1", "articulos?sort=id,asc",
            "articulos?q=a&q=b", "almacenes?sort=descripcion,up", "almacenes?desconocido=1", "almacenes/101/stock?desde=2026-01-01",
            "movimientos-stock?desde=2026-02-30", "movimientos-stock?desde=2026-09-02&hasta=2026-09-01",
            "movimientos-stock?desde=2026-9-01", "movimientos-stock?desde=", "movimientos-stock?sort=fecha,desc,extra",
            "movimientos-stock?page=100001", "almacenes/101/movimientos?hasta=2026-01-01&hasta=2026-01-02"})
    void parametrosInvalidosDevuelven400(String path) throws Exception {
        consultar("1/" + path).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void sinAutenticacionNiEscriturasPublicadas() throws Exception {
        for (String path : new String[]{"articulos", "almacenes", "almacenes/101/stock", "movimientos-stock"}) {
            mvc.perform(get("/api/v1/empresas/1/" + path)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/empresas/1/" + path).contentType("application/json").content("{}")
                    .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        jdbc.update("UPDATE API_Empresas SET activo = FALSE WHERE id_empresa = 1");
        consultar("1/almacenes/101/stock?size=0").andExpect(status().isNotFound());
    }
}
