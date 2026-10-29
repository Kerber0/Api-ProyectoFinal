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
class EconomiaTests extends PostgreSqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    private String token;
    private int usuario;

    record Caso(String tipo, String facturas, String lineas, String movimientos, String idMovimiento,
                int primera, int ajena, int movimientoAjeno, String filtro, int tercero,
                String total, String parcial, String saldoParcial, String permisoFactura, String permisoMovimiento) {
        String ruta() { return "facturas-" + tipo; }
    }
    private Caso caso(String tipo) {
        return tipo.equals("cliente")
                ? new Caso(tipo, "Facturas_Cliente", "Lineas_Factura_Cliente", "cobros", "id_cobro", 5001, 5004, 95004, "cliente", 101, "1210.00", "2420.00", "1420.00", "FACTURAS_CLIENTE", "COBROS")
                : new Caso(tipo, "Facturas_Proveedor", "Lineas_Factura_Proveedor", "pagos", "id_pago", 6001, 6004, 96004, "proveedor", 201, "484.00", "968.00", "468.00", "FACTURAS_PROVEEDOR", "PAGOS");
    }
    @BeforeEach
    void preparar() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("dorna_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dorna_demo_metadata WHERE id = 1", Integer.class)).isEqualTo(1);
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-economia', ?)", passwords.encode("clave-ficticia-pruebas"));
        usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario = 'test-economia'", Integer.class);
        asignar(1);
        token = auth.login(new AuthDtos.Login("test-economia", "clave-ficticia-pruebas")).accessToken();
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
    private int linea(Caso c) {
        return jdbc.queryForObject("SELECT MIN(id_linea) FROM " + c.lineas + " WHERE id_factura = ?", Integer.class, c.primera);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void facturasPaginadasFiltradasYOriginales(String tipo) throws Exception {
        var c = caso(tipo);
        consultar("1/" + c.ruta() + "?size=1&sort=numero,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].id").value(c.primera + 2))
                .andExpect(jsonPath("$.items[0].empresaId").value(1));
        consultar("1/" + c.ruta() + "?size=1&page=1&sort=numero,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(c.primera + 1));
        consultar("1/" + c.ruta() + "?q=002&estado=PARCIAL&desde=2026-09-15&hasta=2026-09-15&" + c.filtro + "=" + c.tercero)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].total").value(c.parcial));
        consultar("1/" + c.ruta() + "?" + c.filtro + "=" + (c.tercero + 1)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/" + c.ruta() + "?page=9&size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
        jdbc.update("UPDATE " + c.facturas + " SET total = 123.45 WHERE id_factura = ?", c.primera);
        consultar("1/" + c.ruta() + "/" + c.primera).andExpect(status().isOk())
                .andExpect(jsonPath("$.empresaId").value(1)).andExpect(jsonPath("$.total").value("123.45"));
        mvc.perform(get("/api/v1/empresas/1/" + c.ruta()).param("q", "%").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void saldosPendientesParcialesYPagadosSinMultiplicarPorLineas(String tipo) throws Exception {
        var c = caso(tipo);
        consultar("1/" + c.ruta() + "/" + c.primera + "/saldo").andExpect(status().isOk())
                .andExpect(jsonPath("$.movimientosRegistrados").value("0.00"))
                .andExpect(jsonPath("$.saldoAritmetico").value(c.total));
        jdbc.update("INSERT INTO " + c.lineas + " (id_factura) VALUES (?)", c.primera + 1);
        consultar("1/" + c.ruta() + "/" + (c.primera + 1) + "/saldo").andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoAritmetico").value(c.saldoParcial))
                .andExpect(jsonPath("$.regla").value("DEMO_TOTAL_MENOS_MOVIMIENTOS"));
        consultar("1/" + c.ruta() + "/" + (c.primera + 2) + "/saldo").andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoAritmetico").value("0.00"));
        consultar("1/vencimientos-" + tipo + "?desde=2026-10-15&hasta=2026-10-15").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].saldoAritmetico").value(c.saldoParcial));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void saldoConservaSignosSinInventarAnulacionesNiEstados(String tipo) throws Exception {
        var c = caso(tipo);
        jdbc.update("UPDATE " + c.facturas + " SET total = 10, id_estado = (SELECT id_estado FROM Estados_Factura WHERE descripcion = 'PAGADA') WHERE id_factura = ?", c.primera);
        jdbc.update("INSERT INTO " + c.movimientos + " (id_factura, fecha, importe) VALUES (?, '2026-09-01', 20), (?, '2026-09-02', -3)", c.primera, c.primera);
        consultar("1/" + c.ruta() + "/" + c.primera + "/saldo").andExpect(status().isOk())
                .andExpect(jsonPath("$.movimientosRegistrados").value("17.00"))
                .andExpect(jsonPath("$.saldoAritmetico").value("-7.00"));
        consultar("1/" + c.ruta() + "/" + c.primera).andExpect(status().isOk())
                .andExpect(jsonPath("$.estadoOriginal").value("PAGADA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void movimientosUsanFechaEfectivaYSoloSuFactura(String tipo) throws Exception {
        var c = caso(tipo);
        String response = consultar("1/" + c.movimientos + "?desde=2026-10-01&hasta=2026-10-01&factura=" + (c.primera + 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].importe").value(tipo.equals("cliente") ? "600.00" : "200.00"))
                .andReturn().getResponse().getContentAsString();
        int id = json.readTree(response).get("items").get(0).get("id").asInt();
        consultar("1/" + c.movimientos + "/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.facturaId").value(c.primera + 1));
        consultar("1/" + c.ruta() + "/" + c.primera + "/" + c.movimientos).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/" + c.ruta() + "/" + (c.primera + 1) + "/" + c.movimientos + "?size=1&sort=fecha,desc")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].fecha").value("2026-10-01"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void vencimientosNoSonMovimientosNiFiltranElAcumulado(String tipo) throws Exception {
        var c = caso(tipo);
        consultar("1/vencimientos-" + tipo + "?desde=2026-09-30&hasta=2026-09-30&size=1&sort=fecha,asc")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].facturaId").value(c.primera));
        consultar("1/vencimientos-" + tipo + "?desde=2026-09-30&hasta=2026-09-30&size=1&page=1&sort=fecha,asc")
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].saldoAritmetico").value("0.00"));
        jdbc.update("UPDATE " + c.facturas + " SET fecha_vencimiento = NULL WHERE id_factura = ?", c.primera);
        var detalle = consultar("1/" + c.ruta() + "/" + c.primera).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(detalle).get("fechaVencimiento").isNull()).isTrue();
        consultar("1/vencimientos-" + tipo).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        consultar("1/" + c.ruta() + "/" + c.primera + "/saldo").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void lineasConNulosPertenecenASuFactura(String tipo) throws Exception {
        var c = caso(tipo);
        int id = linea(c);
        consultar("1/" + c.ruta() + "/" + c.primera + "/lineas").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        consultar("1/" + c.ruta() + "/" + c.primera + "/lineas/" + id).andExpect(status().isOk()).andExpect(jsonPath("$.cantidad").value("1.00"));
        consultar("1/" + c.ruta() + "/" + (c.primera + 1) + "/lineas/" + id).andExpect(status().isNotFound());
        jdbc.update("UPDATE " + c.lineas + " SET descripcion = NULL, cantidad = NULL, precio = NULL, descuento = NULL, iva = NULL, importe = NULL WHERE id_linea = ?", id);
        String response = consultar("1/" + c.ruta() + "/" + c.primera + "/lineas/" + id).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String field : new String[]{"descripcion", "cantidad", "precio", "descuento", "iva", "importe"}) {
            assertThat(json.readTree(response).get(field).isNull()).as(field).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void permisoDeFacturaNoRevelaMovimientosNiSaldos(String tipo) throws Exception {
        var c = caso(tipo);
        retirar(c.permisoMovimiento);
        consultar("1/" + c.ruta()).andExpect(status().isOk());
        consultar("1/" + c.ruta() + "/" + c.primera).andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoAritmetico").doesNotExist()).andExpect(jsonPath("$.movimientosRegistrados").doesNotExist());
        consultar("1/" + c.ruta() + "/" + c.primera + "/lineas").andExpect(status().isOk());
        for (String path : new String[]{c.movimientos, c.movimientos + "/" + c.movimientoAjeno,
                c.ruta() + "/" + c.primera + "/saldo", c.ruta() + "/" + c.primera + "/" + c.movimientos, "vencimientos-" + tipo}) {
            consultar("1/" + path).andExpect(status().isNotFound());
        }
        var otra = caso(tipo.equals("cliente") ? "proveedor" : "cliente");
        consultar("1/" + otra.movimientos).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void permisoDeMovimientoNoConcedeAccesoSinFactura(String tipo) throws Exception {
        var c = caso(tipo);
        retirar(c.permisoFactura);
        for (String path : new String[]{c.ruta(), c.ruta() + "/" + c.primera, c.ruta() + "/" + c.primera + "/lineas",
                c.movimientos, c.ruta() + "/" + c.primera + "/saldo", "vencimientos-" + tipo}) {
            consultar("1/" + path).andExpect(status().isNotFound());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void accesoADosEmpresasMantienePertenencias(String tipo) throws Exception {
        var c = caso(tipo);
        consultar("2/" + c.ruta()).andExpect(status().isNotFound());
        asignar(2);
        consultar("2/" + c.ruta()).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        consultar("2/" + c.ruta() + "/" + c.ajena + "/saldo").andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoAritmetico").value(tipo.equals("cliente") ? "81.00" : "200.00"));
        consultar("2/" + c.movimientos + "/" + c.movimientoAjeno).andExpect(status().isOk());
        for (String path : new String[]{c.ruta() + "/" + c.ajena, c.ruta() + "/" + c.ajena + "/lineas",
                c.ruta() + "/" + c.ajena + "/saldo", c.ruta() + "/" + c.ajena + "/" + c.movimientos,
                c.movimientos + "/" + c.movimientoAjeno, c.ruta() + "/999999", c.movimientos + "/999999"}) {
            consultar("1/" + path).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        }
        consultar("2/" + c.ruta() + "/" + c.primera).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM API_Factura_Cliente_Empresa WHERE id_factura = 5002",
            "UPDATE API_Factura_Cliente_Empresa SET id_empresa = 2 WHERE id_factura = 5002",
            "UPDATE Facturas_Cliente SET id_cliente = 102 WHERE id_factura = 5002",
            "UPDATE Facturas_Cliente SET id_obra = 1003 WHERE id_factura = 5002",
            "DELETE FROM API_Obra_Empresa WHERE id_obra = 1001",
            "DELETE FROM API_Cliente_Empresa WHERE id_cliente = 101"})
    void incoherenciasOcultanFacturaClienteSusLineasCobrosYSaldo(String sql) throws Exception {
        jdbc.update(sql);
        consultar("1/facturas-cliente/5002").andExpect(status().isNotFound());
        consultar("1/facturas-cliente/5002/lineas").andExpect(status().isNotFound());
        consultar("1/facturas-cliente/5002/cobros").andExpect(status().isNotFound());
        consultar("1/facturas-cliente/5002/saldo").andExpect(status().isNotFound());
        consultar("1/facturas-cliente?q=002").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        consultar("1/cobros?factura=5002").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        consultar("1/vencimientos-cliente?desde=2026-10-15&hasta=2026-10-15").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM API_Factura_Proveedor_Empresa WHERE id_factura = 6002",
            "UPDATE API_Factura_Proveedor_Empresa SET id_empresa = 2 WHERE id_factura = 6002",
            "DELETE FROM API_Proveedor_Empresa WHERE id_proveedor = 201"})
    void incoherenciasOcultanFacturaProveedorSusPagosYSaldo(String sql) throws Exception {
        jdbc.update(sql);
        consultar("1/facturas-proveedor/6002").andExpect(status().isNotFound());
        consultar("1/facturas-proveedor/6002/pagos").andExpect(status().isNotFound());
        consultar("1/facturas-proveedor/6002/saldo").andExpect(status().isNotFound());
        consultar("1/pagos?factura=6002").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/vencimientos-proveedor?desde=2026-10-15&hasta=2026-10-15").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void facturaSinObraSeConservaYDatosPrivadosNoSeExponen() throws Exception {
        jdbc.update("UPDATE Facturas_Cliente SET id_obra = NULL, observaciones = 'PRIVADO' WHERE id_factura = 5001");
        jdbc.update("UPDATE Proveedores SET iban = 'PRIVADO' WHERE id_proveedor = 201");
        consultar("1/facturas-cliente/5001").andExpect(status().isOk()).andExpect(jsonPath("$.obraId").isEmpty())
                .andExpect(jsonPath("$.observaciones").doesNotExist());
        consultar("1/facturas-cliente?obra=1001").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        String factura = consultar("1/facturas-proveedor/6001").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(factura).doesNotContain("iban", "PRIVADO", "observaciones", "numeroOperacion");
        assertThat(json.readTree(factura).get("tercero").properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "codigo", "nombre");
        String movimiento = consultar("1/cobros?size=1").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(movimiento).get("items").get(0).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "facturaId", "fecha", "importe", "metodoPago");
    }

    @ParameterizedTest
    @ValueSource(strings = {"facturas-cliente?size=101", "facturas-proveedor?page=-1", "facturas-cliente?cliente=0",
            "facturas-proveedor?proveedor=1&proveedor=2", "facturas-proveedor?obra=1001", "facturas-cliente?proveedor=201",
            "cobros?factura=-1", "pagos?sort=importe,asc", "vencimientos-cliente?desde=2026-02-30",
            "vencimientos-proveedor?desde=2026-10-01&hasta=2026-09-01", "facturas-cliente?hasta=2026-9-01",
            "facturas-cliente/5002/cobros?factura=5004", "facturas-proveedor/6002/pagos?proveedor=202",
            "facturas-cliente/5001/lineas?q=x", "vencimientos-cliente?estado=PAGADA", "cobros?desde=2026-09-01&desde=2026-09-02"})
    void parametrosInvalidosDevuelven400(String path) throws Exception {
        consultar("1/" + path).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos").isNotEmpty());
    }

    @Test
    void sesionRevocadaYEscriturasDenegadas() throws Exception {
        mvc.perform(get("/api/v1/empresas/1/cobros")).andExpect(status().isUnauthorized());
        for (String path : new String[]{"facturas-cliente", "facturas-proveedor", "cobros", "pagos"}) {
            mvc.perform(post("/api/v1/empresas/1/" + path).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        jdbc.update("UPDATE API_Sesiones SET revocada = TRUE WHERE id_usuario = ?", usuario);
        consultar("1/facturas-cliente/5001/saldo").andExpect(status().isUnauthorized());
        consultar("1/pagos").andExpect(status().isUnauthorized());
    }
}
