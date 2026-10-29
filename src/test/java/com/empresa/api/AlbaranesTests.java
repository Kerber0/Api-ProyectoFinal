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
class AlbaranesTests extends PostgreSqlIntegrationTest {
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
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-albaranes', ?)", passwords.encode("clave-ficticia-pruebas"));
        usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario = 'test-albaranes'", Integer.class);
        asignar(1);
        token = auth.login(new AuthDtos.Login("test-albaranes", "clave-ficticia-pruebas")).accessToken();
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

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void cabecerasYLineasMinimasConImportesHistoricos(String tipo) throws Exception {
        boolean cliente = tipo.equals("cliente");
        int id = cliente ? 99001 : 99101, lineaId = cliente ? 99201 : 99301;
        String tabla = cliente ? "Albaranes_Cliente" : "Albaranes_Proveedor";
        jdbc.update("UPDATE " + tabla + " SET observaciones = 'PRIVADO', total = 999.99 WHERE id_albaran = ?", id);
        jdbc.update("UPDATE Articulos SET precio_coste = 888, precio_venta = 999, descripcion = 'CATALOGO ACTUAL' WHERE referencia = 'DEMO-ART-001'");
        String ruta = "1/albaranes-" + tipo + "/" + id;
        var body = consultar(ruta).andExpect(status().isOk()).andExpect(jsonPath("$.total").value("999.99"))
                .andExpect(jsonPath("$.tipo").value(tipo.toUpperCase(java.util.Locale.ROOT)))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "tipo", "numero", "fecha", "estado", "tercero", "facturaId", "subtotal", "descuento", "impuestos", "total");
        assertThat(body).doesNotContain("PRIVADO", "iban", "email", "nif", "observaciones", "facturado", "recibido", "pendiente");
        assertThat(json.readTree(body).get("tercero").properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "codigo", "nombre");
        var linea = consultar(ruta + "/lineas/" + lineaId).andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidad").value("1.00"))
                .andExpect(jsonPath("$.precio").value(cliente ? "150.00" : "100.00"))
                .andExpect(jsonPath("$.importe").value(cliente ? "150.00" : "100.00"))
                .andReturn().getResponse().getContentAsString();
        assertThat(linea).doesNotContain("CATALOGO ACTUAL");
        assertThat(json.readTree(linea).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "albaranId", "tipo", "referenciaArticulo", "descripcion", "cantidad", "precio", "descuento", "iva", "importe");
    }

    @Test
    void nulosYLineasLibresSeConservanSinInventarImportes() throws Exception {
        var cliente = json.readTree(consultar("1/albaranes-cliente/99002").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(cliente.get("facturaId").isNull()).isTrue();
        var linea = json.readTree(consultar("1/albaranes-cliente/99002/lineas/99203").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(linea.get("descripcion").isNull()).isTrue();
        assertThat(linea.get("importe").isNull()).isTrue();
        var libre = json.readTree(consultar("1/albaranes-proveedor/99105/lineas/99305").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (String campo : new String[]{"referenciaArticulo", "descripcion", "importe"}) {
            assertThat(libre.get(campo).isNull()).isTrue();
        }
        consultar("1/albaranes-cliente/99001/lineas/99202").andExpect(status().isOk()).andExpect(jsonPath("$.importe").value("50.00"));
        consultar("1/albaranes-proveedor/99104").andExpect(status().isOk());
    }

    @Test
    void relacionesConFacturaSonExplicitasYNoMultiplicanCabeceras() throws Exception {
        consultar("1/facturas-cliente/5001/albaranes").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].facturaId").value(5001));
        consultar("1/facturas-proveedor/6001/albaranes?sort=fecha,asc").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].id").value(99101))
                .andExpect(jsonPath("$.items[1].id").value(99102)).andExpect(jsonPath("$.items[0].facturaId").value(6001));
        consultar("1/albaranes-cliente").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        consultar("1/albaranes-proveedor").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(4));
        consultar("1/facturas-cliente/5002/albaranes").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/facturas-proveedor/6002/albaranes").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/albaranes-cliente/99004/lineas").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/albaranes-proveedor/99104/lineas").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void facturaOpcionalNoSeDeduceDeEstadoNiDeImportes(String tipo) throws Exception {
        boolean cliente = tipo.equals("cliente");
        String tabla = cliente ? "Albaranes_Cliente" : "Albaranes_Proveedor";
        int id = cliente ? 99001 : 99101, factura = cliente ? 5001 : 6001;
        jdbc.update("UPDATE " + tabla + " SET id_factura = NULL WHERE id_albaran = ?", id);
        var body = consultar("1/albaranes-" + tipo + "/" + id).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("facturaId").isNull()).isTrue();
        consultar("1/facturas-" + tipo + "/" + factura + "/albaranes").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(cliente ? 0 : 1));
    }

    @Test
    void variasCabecerasClientePuedenCompartirFacturaSinRecalcularla() throws Exception {
        String total = jdbc.queryForObject("SELECT total FROM Facturas_Cliente WHERE id_factura = 5001", String.class);
        jdbc.update("UPDATE Albaranes_Cliente SET id_factura = 5001 WHERE id_albaran = 99002");
        consultar("1/facturas-cliente/5001/albaranes?size=1&page=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].id").value(99002));
        assertThat(jdbc.queryForObject("SELECT total FROM Facturas_Cliente WHERE id_factura = 5001", String.class)).isEqualTo(total);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void paginacionFiltrosFechasInclusivasYBusquedaLiteral(String tipo) throws Exception {
        boolean cliente = tipo.equals("cliente");
        int primero = cliente ? 99001 : 99101, segundo = primero + 1;
        String ruta = "1/albaranes-" + tipo;
        String tercero = cliente ? "cliente=101&factura=5001" : "proveedor=201&factura=6001";
        consultar(ruta + "?" + tercero + "&desde=2026-09-10&hasta=2026-09-10&estado=PENDIENTE&q=001")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(primero));
        consultar(ruta + "?size=1&page=1&sort=numero,asc").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPages").value(cliente ? 3 : 4)).andExpect(jsonPath("$.items[0].id").value(segundo));
        consultar(ruta + "?size=1&page=99").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar(ruta + "/" + primero + "/lineas?size=1&page=1&sort=id,asc").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].id").value(cliente ? 99202 : 99302));
        String tabla = cliente ? "Albaranes_Cliente" : "Albaranes_Proveedor";
        jdbc.update("UPDATE " + tabla + " SET fecha = '2026-09-10' WHERE id_albaran = ?", segundo);
        consultar(ruta + "?size=1&page=1&sort=fecha,asc").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(segundo));
        for (String q : new String[]{"%", "_", "!", "' OR 1=1 --"}) {
            mvc.perform(get("/api/v1/empresas/" + ruta).param("q", q).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2/albaranes-cliente", "2/albaranes-proveedor", "1/albaranes-cliente/99003", "1/albaranes-proveedor/99103",
            "1/albaranes-cliente/99003/lineas", "1/albaranes-proveedor/99103/lineas", "1/albaranes-cliente/99001/lineas/99204",
            "1/albaranes-proveedor/99101/lineas/99304", "1/albaranes-cliente/99001/lineas/99203", "1/albaranes-proveedor/99101/lineas/99303",
            "1/albaranes-cliente/999999", "1/albaranes-proveedor/999999", "1/facturas-cliente/5004/albaranes", "1/facturas-proveedor/6004/albaranes",
            "1/facturas-cliente/999999/albaranes", "1/facturas-proveedor/999999/albaranes", "1/albaranes-cliente/99101", "1/albaranes-proveedor/99001"})
    void accesosCruzadosEInexistentesDevuelven404(String ruta) throws Exception {
        consultar(ruta).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void dosEmpresasNoMezclanRecursosYFiltrosAjenosDevuelvenVacio() throws Exception {
        asignar(2);
        consultar("2/albaranes-cliente").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        consultar("2/albaranes-proveedor").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        consultar("2/albaranes-cliente/99003/lineas/99204").andExpect(status().isOk());
        consultar("2/albaranes-proveedor/99103/lineas/99304").andExpect(status().isOk());
        consultar("2/albaranes-cliente/99001").andExpect(status().isNotFound());
        consultar("2/albaranes-proveedor/99101").andExpect(status().isNotFound());
        for (String ruta : new String[]{"albaranes-cliente?cliente=102", "albaranes-cliente?factura=5004",
                "albaranes-proveedor?proveedor=202", "albaranes-proveedor?factura=6004"}) {
            consultar("1/" + ruta).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void lineasConArticuloAjenoOcultanTodoElDocumento(String tipo) throws Exception {
        boolean cliente = tipo.equals("cliente");
        int albaran = cliente ? 99001 : 99101;
        String tabla = cliente ? "Lineas_Albaran_Cliente" : "Lineas_Albaran_Proveedor";
        jdbc.update("UPDATE " + tabla + " SET referencia_articulo = 'DEMO-ART-003' WHERE id_linea = ?", cliente ? 99201 : 99301);
        String ruta = "1/albaranes-" + tipo;
        consultar(ruta + "/" + albaran).andExpect(status().isNotFound());
        consultar(ruta + "/" + albaran + "/lineas").andExpect(status().isNotFound());
        consultar(ruta + "/" + albaran + "/lineas/" + (cliente ? 99202 : 99302)).andExpect(status().isNotFound());
        consultar(ruta).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(cliente ? 2 : 3));
        consultar(cliente ? "1/facturas-cliente/5001/albaranes" : "1/facturas-proveedor/6001/albaranes").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(cliente ? 0 : 1));
    }

    @Test
    void articuloSinPertenenciaNoInvalidaOtrosAlbaranesDeLaFactura() throws Exception {
        jdbc.update("DELETE FROM API_Articulo_Empresa WHERE referencia_articulo = 'DEMO-ART-002'");
        consultar("1/albaranes-cliente/99002").andExpect(status().isNotFound());
        consultar("1/albaranes-proveedor/99101").andExpect(status().isNotFound());
        // El segundo albaran solo contiene ART-001: la factura no hereda lineas del pedido.
        consultar("1/albaranes-proveedor/99102").andExpect(status().isOk());
        consultar("1/facturas-proveedor/6001/albaranes").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        consultar("1/albaranes-proveedor/99105/lineas/99305").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"factura-ajena", "factura-otro-cliente", "factura-sin-asignar", "obra-otro-cliente", "obra-sin-asignar", "cliente-ajeno", "albaran-sin-asignar"})
    void relacionesClienteIncoherentesOcultanCabeceraYLineas(String caso) throws Exception {
        switch (caso) {
            case "factura-ajena" -> jdbc.update("UPDATE Albaranes_Cliente SET id_factura = 5004 WHERE id_albaran = 99001");
            case "factura-otro-cliente" -> {
                jdbc.update("UPDATE Facturas_Cliente SET id_cliente = 102, id_obra = NULL WHERE id_factura = 5001");
            }
            case "factura-sin-asignar" -> jdbc.update("DELETE FROM API_Factura_Cliente_Empresa WHERE id_factura = 5001");
            case "obra-otro-cliente" -> jdbc.update("UPDATE Obras SET id_cliente = 102 WHERE id_obra = 1001");
            case "obra-sin-asignar" -> jdbc.update("DELETE FROM API_Obra_Empresa WHERE id_obra = 1001");
            case "cliente-ajeno" -> jdbc.update("UPDATE Albaranes_Cliente SET id_cliente = 102 WHERE id_albaran = 99001");
            case "albaran-sin-asignar" -> jdbc.update("DELETE FROM API_Albaran_Cliente_Empresa WHERE id_albaran = 99001");
        }
        consultar("1/albaranes-cliente/99001").andExpect(status().isNotFound());
        consultar("1/albaranes-cliente/99001/lineas/99202").andExpect(status().isNotFound());
        consultar("1/albaranes-cliente").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"factura-ajena", "factura-otro-proveedor", "factura-sin-asignar", "proveedor-ajeno", "albaran-sin-asignar"})
    void relacionesProveedorIncoherentesOcultanCabeceraYLineas(String caso) throws Exception {
        switch (caso) {
            case "factura-ajena" -> jdbc.update("UPDATE Albaranes_Proveedor SET id_factura = 6004 WHERE id_albaran = 99101");
            case "factura-otro-proveedor" -> {
                jdbc.update("UPDATE Facturas_Proveedor SET id_proveedor = 202 WHERE id_factura = 6001");
            }
            case "factura-sin-asignar" -> jdbc.update("DELETE FROM API_Factura_Proveedor_Empresa WHERE id_factura = 6001");
            case "proveedor-ajeno" -> jdbc.update("UPDATE Albaranes_Proveedor SET id_proveedor = 202 WHERE id_albaran = 99101");
            case "albaran-sin-asignar" -> jdbc.update("DELETE FROM API_Albaran_Proveedor_Empresa WHERE id_albaran = 99101");
        }
        consultar("1/albaranes-proveedor/99101").andExpect(status().isNotFound());
        consultar("1/albaranes-proveedor/99101/lineas/99301").andExpect(status().isNotFound());
        consultar("1/albaranes-proveedor/99104").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALBARANES_CLIENTE", "CLIENTES", "FACTURAS_CLIENTE", "ALBARANES_PROVEEDOR", "PROVEEDORES", "FACTURAS_PROVEEDOR"})
    void exigePermisosPorFuenteSinDarAccesoPorElOtroTipo(String permiso) throws Exception {
        retirar(permiso);
        boolean cliente = java.util.Set.of("ALBARANES_CLIENTE", "CLIENTES", "FACTURAS_CLIENTE").contains(permiso);
        String ruta = "1/albaranes-" + (cliente ? "cliente" : "proveedor");
        int id = cliente ? 99001 : 99101;
        consultar(ruta).andExpect(status().isNotFound());
        consultar(ruta + "/" + id).andExpect(status().isNotFound());
        consultar(ruta + "/" + id + "/lineas").andExpect(status().isNotFound());
        consultar(ruta + "/" + id + "/lineas/" + (cliente ? 99201 : 99301)).andExpect(status().isNotFound());
        consultar(cliente ? "1/facturas-cliente/5001/albaranes" : "1/facturas-proveedor/6001/albaranes").andExpect(status().isNotFound());
        consultar(cliente ? "1/albaranes-proveedor" : "1/albaranes-cliente").andExpect(status().isOk());
    }

    @Test
    void lineasLibresTambienRequierenPermisoDeArticulos() throws Exception {
        retirar("ARTICULOS");
        consultar("1/albaranes-cliente/99001").andExpect(status().isOk());
        consultar("1/albaranes-proveedor/99101").andExpect(status().isOk());
        consultar("1/albaranes-cliente/99001/lineas/99202").andExpect(status().isNotFound());
        consultar("1/albaranes-proveedor/99105/lineas/99305").andExpect(status().isNotFound());
    }

    @Test
    void inactivosConservanHistoricoSinCrearMovimientosDeStock() throws Exception {
        jdbc.update("UPDATE Entidades SET activo = FALSE WHERE id_entidad IN (101, 201)");
        jdbc.update("UPDATE Articulos SET activo = FALSE WHERE referencia = 'DEMO-ART-001'");
        long antes = jdbc.queryForObject("SELECT COUNT(*) FROM Movimientos_Stock", Long.class);
        consultar("1/albaranes-cliente/99001/lineas").andExpect(status().isOk());
        consultar("1/albaranes-proveedor/99101/lineas").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM Movimientos_Stock", Long.class)).isEqualTo(antes);
        consultar("1/almacenes/101/stock?q=DEMO-ART-001").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].cantidad").value("6.50"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"albaranes-cliente?size=0", "albaranes-proveedor?size=101", "albaranes-cliente?page=-1",
            "albaranes-proveedor?page=100001", "albaranes-cliente?desde=2026-02-30", "albaranes-proveedor?desde=2026-10-01&hasta=2026-09-01",
            "albaranes-cliente?cliente=0", "albaranes-cliente?factura=abc", "albaranes-proveedor?proveedor=0", "albaranes-proveedor?factura=abc",
            "albaranes-cliente?cliente=101&cliente=102", "albaranes-proveedor?factura=6001&factura=6002",
            "albaranes-cliente?proveedor=201", "albaranes-proveedor?obra=1001", "facturas-cliente/5001/albaranes?factura=5001",
            "facturas-proveedor/6001/albaranes?factura=6001", "albaranes-cliente?sort=total,asc", "albaranes-proveedor?sort=fecha,ASC",
            "albaranes-cliente/99001/lineas?q=x", "albaranes-proveedor/99101/lineas?desde=2026-01-01",
            "albaranes-cliente?obra=1001", "albaranes-proveedor?pedido=98001",
            "albaranes-cliente?factura=0", "albaranes-proveedor?factura=-1"})
    void filtrosInvalidosDevuelven400(String ruta) throws Exception {
        consultar("1/" + ruta).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void autenticacionDenegacionDeEscriturasYAutorizacionAntesDeFiltros() throws Exception {
        for (String ruta : new String[]{"albaranes-cliente", "albaranes-cliente/99001", "albaranes-cliente/99001/lineas",
                "albaranes-cliente/99001/lineas/99201", "facturas-cliente/5001/albaranes", "albaranes-proveedor", "albaranes-proveedor/99101",
                "albaranes-proveedor/99101/lineas", "albaranes-proveedor/99101/lineas/99301", "facturas-proveedor/6001/albaranes"}) {
            mvc.perform(get("/api/v1/empresas/1/" + ruta)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/empresas/1/" + ruta).contentType("application/json").content("{}")
                    .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        jdbc.update("UPDATE API_Empresas SET activo = FALSE WHERE id_empresa = 1");
        consultar("1/albaranes-cliente?size=0").andExpect(status().isNotFound());
        consultar("1/albaranes-proveedor?size=0").andExpect(status().isNotFound());
    }
}
