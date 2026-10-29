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
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class InformesTests extends PostgreSqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    private String token;
    private int usuario;
    private static final String PERIODO = "?desde=2026-09-01&hasta=2026-10-31";
    private static final List<String> RUTAS = List.of("facturacion-cliente", "facturacion-proveedor",
            "cobros", "pagos", "saldos-cliente", "saldos-proveedor");
    private static final List<String> RUTAS_ESTADOS = List.of("obras-por-estado", "presupuestos-por-estado");
    private static List<String> todasLasRutas() {
        return java.util.stream.Stream.concat(RUTAS.stream(),RUTAS_ESTADOS.stream()).toList();
    }

    record Caso(String tipo, String tabla, String movimientos, int primera, String total,
                String subtotal, String impuestos, String registrado, String septiembre, String octubre,
                String saldo, String totalAjeno, String saldoAjeno, String permisoFactura, String permisoMovimiento) {}
    private Caso caso(String tipo) {
        return tipo.equals("cliente")
                ? new Caso(tipo, "Facturas_Cliente", "cobros", 5001, "4235.00", "3500.00", "735.00", "1605.00", "1005.00", "600.00", "2630.00", "121.00", "81.00", "FACTURAS_CLIENTE", "COBROS")
                : new Caso(tipo, "Facturas_Proveedor", "pagos", 6001, "1694.00", "1400.00", "294.00", "742.00", "542.00", "200.00", "952.00", "242.00", "200.00", "FACTURAS_PROVEEDOR", "PAGOS");
    }
    @BeforeEach
    void preparar() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("dorna_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dorna_demo_metadata WHERE id = 1", Integer.class)).isEqualTo(1);
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-informes', ?)", passwords.encode("clave-ficticia-pruebas"));
        usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario = 'test-informes'", Integer.class);
        asignar(1);
        token = auth.login(new AuthDtos.Login("test-informes", "clave-ficticia-pruebas")).accessToken();
    }
    private void asignar(int empresa) {
        jdbc.update("INSERT INTO API_Accesos_Empresa SELECT ?, ?, id_rol FROM Roles WHERE nombre = 'CONSULTA_RECURSOS_DEMO'", usuario, empresa);
    }
    private void retirar(String permiso) {
        jdbc.update("DELETE FROM Rol_Permiso WHERE id_rol = (SELECT id_rol FROM Roles WHERE nombre = 'CONSULTA_RECURSOS_DEMO') AND id_permiso = (SELECT id_permiso FROM Permisos WHERE nombre = ?)", permiso);
    }
    private ResultActions consultar(String path) throws Exception {
        return mvc.perform(get("/api/v1/empresas/" + path).header("Authorization", "Bearer " + token));
    }
    private JsonNode leer(String path) throws Exception {
        return json.readTree(consultar(path).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private BigDecimal sumar(JsonNode items, String campo) {
        BigDecimal suma = new BigDecimal("0.00");
        for (JsonNode item : items) { suma = suma.add(new BigDecimal(item.get(campo).asText())); }
        return suma;
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void facturacionConciliaConCabecerasYCompletaMesesVacios(String tipo) throws Exception {
        var c = caso(tipo);
        var informe = leer("1/informes/facturacion-" + tipo + "?desde=2026-08-01&hasta=2026-11-30");
        assertThat(informe.get("empresaId").asInt()).isEqualTo(1);
        assertThat(informe.get("tipo").asText()).isEqualTo(tipo.toUpperCase(Locale.ROOT));
        assertThat(informe.get("criterio").asText()).isEqualTo("FACTURAS_POR_FECHA_EMISION");
        assertThat(informe.get("desde").asText()).isEqualTo("2026-08-01");
        assertThat(informe.get("hasta").asText()).isEqualTo("2026-11-30");
        assertThat(informe.get("meses")).hasSize(4);
        for (int i = 0; i < 4; i++) {
            var mes = informe.get("meses").get(i);
            assertThat(mes.get("mes").asText()).isEqualTo("2026-" + String.format(Locale.ROOT, "%02d", 8 + i));
            assertThat(mes.get("valores").get("facturas").asInt()).isEqualTo(i == 1 ? 3 : 0);
            assertThat(mes.get("valores").get("total").asText()).isEqualTo(i == 1 ? c.total : "0.00");
        }
        assertThat(informe.get("totales").get("total").asText()).isEqualTo(c.total);
        assertThat(informe.get("totales").get("subtotal").asText()).isEqualTo(c.subtotal);
        assertThat(informe.get("totales").get("impuestos").asText()).isEqualTo(c.impuestos);
        var facturas = leer("1/facturas-" + tipo + PERIODO).get("items");
        for (String campo : List.of("subtotal", "descuento", "impuestos", "total")) {
            assertThat(new BigDecimal(informe.get("totales").get(campo).asText())).isEqualByComparingTo(sumar(facturas, campo));
        }
        assertThat(informe.get("totales").properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("facturas", "subtotal", "descuento", "impuestos", "total");
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void movimientosConcilianPorFechaEfectivaSinExigirEmisionEnPeriodo(String tipo) throws Exception {
        var c = caso(tipo);
        var informe = leer("1/informes/" + c.movimientos + PERIODO);
        assertThat(informe.get("criterio").asText()).isEqualTo("MOVIMIENTOS_POR_FECHA_EFECTIVA");
        assertThat(informe.get("totales").get("movimientos").asInt()).isEqualTo(3);
        assertThat(informe.get("totales").get("importe").asText()).isEqualTo(c.registrado);
        assertThat(informe.get("meses").get(0).get("valores").get("importe").asText()).isEqualTo(c.septiembre);
        assertThat(informe.get("meses").get(1).get("valores").get("importe").asText()).isEqualTo(c.octubre);
        var items = leer("1/" + c.movimientos + PERIODO).get("items");
        assertThat(new BigDecimal(informe.get("totales").get("importe").asText())).isEqualByComparingTo(sumar(items, "importe"));
        consultar("1/informes/" + c.movimientos + "?desde=2026-10-01&hasta=2026-10-01")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.importe").value(c.octubre))
                .andExpect(jsonPath("$.totales.movimientos").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void saldosConcilianConDetallesSinMultiplicarLineasNiMovimientos(String tipo) throws Exception {
        var c = caso(tipo);
        jdbc.update("INSERT INTO Lineas_Factura_" + (tipo.equals("cliente") ? "Cliente" : "Proveedor") + " (id_factura, importe) VALUES (?, 9999), (?, 9999)", c.primera + 1, c.primera + 1);
        var informe = leer("1/informes/saldos-" + tipo + "?desde=2026-09-01&hasta=2026-09-30");
        assertThat(informe.get("criterio").asText()).isEqualTo("DEMO_FACTURAS_EMITIDAS_MENOS_MOVIMIENTOS_COMPLETOS");
        assertThat(informe.get("totales").get("facturas").asInt()).isEqualTo(3);
        assertThat(informe.get("totales").get("totalFacturas").asText()).isEqualTo(c.total);
        assertThat(informe.get("totales").get("movimientosRegistrados").asText()).isEqualTo(c.registrado);
        assertThat(informe.get("totales").get("saldoAritmetico").asText()).isEqualTo(c.saldo);
        BigDecimal total = BigDecimal.ZERO;
        for (int id = c.primera; id <= c.primera + 2; id++) {
            total = total.add(new BigDecimal(leer("1/facturas-" + tipo + "/" + id + "/saldo").get("saldoAritmetico").asText()));
        }
        assertThat(new BigDecimal(informe.get("totales").get("saldoAritmetico").asText())).isEqualByComparingTo(total);
        consultar("1/informes/facturacion-" + tipo + PERIODO).andExpect(status().isOk()).andExpect(jsonPath("$.totales.total").value(c.total));
        consultar("1/informes/saldos-" + tipo + "?desde=2026-10-01&hasta=2026-10-31")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.saldoAritmetico").value("0.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void importesOriginalesSignosYMovimientosFueraDePeriodoSeConservan(String tipo) throws Exception {
        var c = caso(tipo);
        jdbc.update("UPDATE " + c.tabla + " SET fecha = '2026-12-15', subtotal = 11.11, descuento = 0.12, impuestos = -2.22, total = 10 WHERE id_factura = ?", c.primera);
        jdbc.update("INSERT INTO " + c.movimientos + " (id_factura, fecha, importe) VALUES (?, '2027-01-01', 20), (?, '2026-12-15', -3)", c.primera, c.primera);
        consultar("1/informes/facturacion-" + tipo + "?desde=2026-12-15&hasta=2026-12-15")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.total").value("10.00"))
                .andExpect(jsonPath("$.totales.subtotal").value("11.11"))
                .andExpect(jsonPath("$.totales.descuento").value("0.12"))
                .andExpect(jsonPath("$.totales.impuestos").value("-2.22"));
        consultar("1/informes/saldos-" + tipo + "?desde=2026-12-15&hasta=2026-12-15")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.movimientosRegistrados").value("17.00"))
                .andExpect(jsonPath("$.totales.saldoAritmetico").value("-7.00"));
        consultar("1/informes/" + c.movimientos + "?desde=2026-12-15&hasta=2026-12-15")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.importe").value("-3.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void dosEmpresasAutorizadasNoMezclanCifras(String tipo) throws Exception {
        var c = caso(tipo);
        consultar("2/informes/facturacion-" + tipo + PERIODO).andExpect(status().isNotFound());
        asignar(2);
        consultar("2/informes/facturacion-" + tipo + PERIODO).andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.facturas").value(1)).andExpect(jsonPath("$.totales.total").value(c.totalAjeno));
        consultar("2/informes/saldos-" + tipo + PERIODO).andExpect(status().isOk()).andExpect(jsonPath("$.totales.saldoAritmetico").value(c.saldoAjeno));
        consultar("2/informes/" + c.movimientos + PERIODO).andExpect(status().isOk()).andExpect(jsonPath("$.totales.movimientos").value(1));
        consultar("1/informes/facturacion-" + tipo + PERIODO).andExpect(status().isOk()).andExpect(jsonPath("$.totales.total").value(c.total));
        consultar("1/informes/saldos-" + tipo + PERIODO).andExpect(status().isOk()).andExpect(jsonPath("$.totales.saldoAritmetico").value(c.saldo));
        consultar("1/informes/" + c.movimientos + PERIODO).andExpect(status().isOk()).andExpect(jsonPath("$.totales.importe").value(c.registrado));
    }

    @Test
    void grupoExigeLasCuatroSociedadesYSuTotalEsLaSumaAgregada() throws Exception {
        consultar("1/informes/grupo"+PERIODO).andExpect(status().isNotFound());
        consultar("1/informes/grupo?desde=invalida").andExpect(status().isNotFound());
        asignar(2); asignar(3); asignar(4);
        for(int empresa=3;empresa<=4;empresa++) {
            int cliente=97000+empresa,proveedor=98000+empresa;
            jdbc.update("""
                    INSERT INTO Facturas_Cliente(id_factura,numero,id_serie,fecha,fecha_vencimiento,subtotal,descuento,impuestos,total,id_estado,id_cliente)
                    SELECT ?,?,id_serie,'2026-09-10','2026-10-10',100,0,21,121,id_estado,id_cliente
                    FROM Facturas_Cliente WHERE id_factura=5001
                    """,cliente,"GRUPO-FC-"+empresa);
            jdbc.update("INSERT INTO API_Factura_Cliente_Empresa VALUES (?,?)",cliente,empresa);
            jdbc.update("INSERT INTO Cobros(id_factura,fecha,importe) VALUES (?,'2026-09-20',?)",cliente,empresa*10);
            jdbc.update("""
                    INSERT INTO Facturas_Proveedor(id_factura,numero,id_serie,fecha,fecha_vencimiento,subtotal,descuento,impuestos,total,id_estado,id_proveedor)
                    SELECT ?,?,id_serie,'2026-09-10','2026-10-10',50,0,10.50,60.50,id_estado,id_proveedor
                    FROM Facturas_Proveedor WHERE id_factura=6001
                    """,proveedor,"GRUPO-FP-"+empresa);
            jdbc.update("INSERT INTO API_Factura_Proveedor_Empresa VALUES (?,?)",proveedor,empresa);
            jdbc.update("INSERT INTO Pagos(id_factura,fecha,importe) VALUES (?,'2026-09-21',?)",proveedor,empresa*5);
        }
        var grupo=leer("1/informes/grupo"+PERIODO);
        assertThat(grupo.get("criterio").asText()).isEqualTo("AGREGADO_SIN_ELIMINACIONES_INTEREMPRESA");
        assertThat(grupo.get("empresas")).hasSize(4);
        BigDecimal facturado=BigDecimal.ZERO,cobrado=BigDecimal.ZERO,pagado=BigDecimal.ZERO;
        for(var sociedad:grupo.get("empresas")) {
            facturado=facturado.add(new BigDecimal(sociedad.at("/valores/facturacionClientes/total").asText()));
            cobrado=cobrado.add(new BigDecimal(sociedad.at("/valores/cobrosRegistrados/importe").asText()));
            pagado=pagado.add(new BigDecimal(sociedad.at("/valores/pagosRegistrados/importe").asText()));
        }
        assertThat(new BigDecimal(grupo.at("/totales/facturacionClientes/total").asText())).isEqualByComparingTo(facturado);
        assertThat(new BigDecimal(grupo.at("/totales/cobrosRegistrados/importe").asText())).isEqualByComparingTo(cobrado);
        assertThat(new BigDecimal(grupo.at("/totales/pagosRegistrados/importe").asText())).isEqualByComparingTo(pagado);
        assertThat(new BigDecimal(grupo.at("/totales/flujoCajaRegistrado").asText())).isEqualByComparingTo(cobrado.subtract(pagado));
        jdbc.update("UPDATE API_Empresas SET activo=false WHERE id_empresa=4");
        consultar("1/informes/grupo"+PERIODO).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void permisoDeInformesNoSustituyeAlDeFactura(String tipo) throws Exception {
        var c = caso(tipo);
        retirar(c.permisoFactura);
        for (String ruta : List.of("facturacion-" + tipo, "saldos-" + tipo, c.movimientos)) {
            consultar("1/informes/" + ruta + PERIODO).andExpect(status().isNotFound());
        }
        consultar("1/informes/facturacion-" + (tipo.equals("cliente") ? "proveedor" : "cliente") + PERIODO).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cliente", "proveedor"})
    void permisoDeMovimientoSeExigeSoloEnMovimientosYSaldos(String tipo) throws Exception {
        var c = caso(tipo);
        retirar(c.permisoMovimiento);
        consultar("1/informes/" + c.movimientos + PERIODO).andExpect(status().isNotFound());
        consultar("1/informes/saldos-" + tipo + PERIODO).andExpect(status().isNotFound());
        consultar("1/informes/facturacion-" + tipo + PERIODO).andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.movimientosRegistrados").doesNotExist())
                .andExpect(jsonPath("$.totales.saldoAritmetico").doesNotExist());
    }

    @Test
    void permisoInformesEsObligatorioYSeVerificaAntesDeParametros() throws Exception {
        retirar("INFORMES");
        for (String ruta : todasLasRutas()) {
            consultar("1/informes/" + ruta + PERIODO).andExpect(status().isNotFound());
            consultar("1/informes/" + ruta + "?desde=incorrecto").andExpect(status().isNotFound());
        }
        consultar("1/facturas-cliente").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM API_Factura_Cliente_Empresa WHERE id_factura = 5002",
            "UPDATE API_Factura_Cliente_Empresa SET id_empresa = 2 WHERE id_factura = 5002",
            "UPDATE Facturas_Cliente SET id_cliente = 102 WHERE id_factura = 5002",
            "UPDATE Facturas_Cliente SET id_obra = 1003 WHERE id_factura = 5002",
            "DELETE FROM API_Obra_Empresa WHERE id_obra = 1001",
            "DELETE FROM API_Cliente_Empresa WHERE id_cliente = 101"})
    void facturaClienteIncoherenteNoEntraEnNingunaAgregacion(String sql) throws Exception {
        jdbc.update(sql);
        consultar("1/informes/facturacion-cliente?desde=2026-09-15&hasta=2026-09-15")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.facturas").value(0)).andExpect(jsonPath("$.totales.total").value("0.00"));
        consultar("1/informes/cobros?desde=2026-10-01&hasta=2026-10-01")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.movimientos").value(0)).andExpect(jsonPath("$.totales.importe").value("0.00"));
        consultar("1/informes/saldos-cliente?desde=2026-09-15&hasta=2026-09-15")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.facturas").value(0)).andExpect(jsonPath("$.totales.saldoAritmetico").value("0.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE FROM API_Factura_Proveedor_Empresa WHERE id_factura = 6002",
            "UPDATE API_Factura_Proveedor_Empresa SET id_empresa = 2 WHERE id_factura = 6002",
            "DELETE FROM API_Proveedor_Empresa WHERE id_proveedor = 201"})
    void facturaProveedorIncoherenteNoEntraEnNingunaAgregacion(String sql) throws Exception {
        jdbc.update(sql);
        consultar("1/informes/facturacion-proveedor?desde=2026-09-15&hasta=2026-09-15")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.facturas").value(0));
        consultar("1/informes/pagos?desde=2026-10-01&hasta=2026-10-01")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.movimientos").value(0));
        consultar("1/informes/saldos-proveedor?desde=2026-09-15&hasta=2026-09-15")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totales.facturas").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?desde=2026-09-01", "?hasta=2026-09-30",
            "?desde=&hasta=2026-09-30", "?desde=2026-09-01&hasta=",
            "?desde=2026-02-30&hasta=2026-09-30", "?desde=2026-9-01&hasta=2026-09-30",
            "?desde=0999-01-01&hasta=1000-01-01", "?desde=2026-10-01&hasta=2026-09-01",
            "?desde=2024-01-31&hasta=2026-01-01", "?desde=2026-09-01&hasta=2026-09-30&page=0",
            "?desde=2026-09-01&hasta=2026-09-30&empresa=2",
            "?desde=2026-09-01&desde=2026-09-02&hasta=2026-09-30",
            "?desde=2026-09-01&hasta=2026-09-30&hasta=2026-09-30"})
    void fechasObligatoriasRangoAcotadoYParametrosEstrictos(String query) throws Exception {
        for (String ruta : todasLasRutas()) {
            consultar("1/informes/" + ruta + query).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos").isNotEmpty()).andExpect(header().exists("X-Request-Id"));
        }
    }

    @Test
    void limite24MesesBisiestosYExtremosDelContrato() throws Exception {
        for (String ruta : RUTAS) {
            consultar("1/informes/" + ruta + "?desde=2024-02-29&hasta=2026-01-31").andExpect(status().isOk()).andExpect(jsonPath("$.meses.length()").value(24));
            consultar("1/informes/" + ruta + "?desde=1000-01-01&hasta=1000-01-01").andExpect(status().isOk()).andExpect(jsonPath("$.meses[0].mes").value("1000-01"));
            consultar("1/informes/" + ruta + "?desde=9999-12-31&hasta=9999-12-31").andExpect(status().isOk()).andExpect(jsonPath("$.meses[0].mes").value("9999-12"));
        }
        for(String ruta:RUTAS_ESTADOS) {
            consultar("1/informes/"+ruta+"?desde=2024-02-29&hasta=2026-01-31").andExpect(status().isOk())
                    .andExpect(jsonPath("$.estados").isArray());
            consultar("1/informes/"+ruta+"?desde=1000-01-01&hasta=1000-01-01").andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));
            consultar("1/informes/"+ruta+"?desde=9999-12-31&hasta=9999-12-31").andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"obras","presupuestos"})
    void estadosPreparadosParaGraficosIncluyenCerosOrdenYTotal(String tipo) throws Exception {
        String ruta=tipo+"-por-estado";
        var informe=leer("1/informes/"+ruta+"?desde=2020-01-01&hasta=2021-12-31");
        assertThat(informe.get("empresaId").asInt()).isEqualTo(1);
        assertThat(informe.get("tipo").asText()).isEqualTo(tipo.toUpperCase(Locale.ROOT));
        assertThat(informe.get("criterio").asText()).isEqualTo(tipo.equals("obras")
                ?"ESTADO_ACTUAL_POR_FECHA_INICIO":"ESTADO_ACTUAL_POR_FECHA_EMISION");
        assertThat(informe.get("total").asLong()).isZero();
        assertThat(informe.get("estados")).isNotEmpty();
        int orden=Integer.MIN_VALUE;long suma=0;
        for(var estado:informe.get("estados")) {
            assertThat(estado.properties()).extracting(java.util.Map.Entry::getKey)
                    .containsExactlyInAnyOrder("id","estado","color","orden","cantidad");
            assertThat(estado.get("orden").asInt()).isGreaterThanOrEqualTo(orden);orden=estado.get("orden").asInt();
            suma+=estado.get("cantidad").asLong();
        }
        assertThat(suma).isEqualTo(informe.get("total").asLong());
    }

    @Test
    void filtrosTemporalesYEstadosActualesSonInclusivos() throws Exception {
        var fechaObra=java.time.LocalDate.of(2024,6,15);
        var fechaPresupuesto=java.time.LocalDate.of(2024,7,16);
        jdbc.update("UPDATE Obras SET fecha_inicio=? WHERE id_obra=1001",fechaObra);
        jdbc.update("UPDATE Presupuestos SET fecha=? WHERE id_presupuesto=4001",fechaPresupuesto);
        consultar("1/informes/obras-por-estado?desde="+fechaObra+"&hasta="+fechaObra).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        jdbc.update("UPDATE Obras SET id_estado=(SELECT id_estado FROM Estados_Obra WHERE descripcion='CANCELADA') WHERE id_obra=1001");
        consultar("1/informes/obras-por-estado?desde="+fechaObra+"&hasta="+fechaObra).andExpect(status().isOk())
                .andExpect(jsonPath("$.estados[?(@.estado == 'CANCELADA')].cantidad").value(org.hamcrest.Matchers.contains(1)));
        consultar("1/informes/presupuestos-por-estado?desde="+fechaPresupuesto+"&hasta="+fechaPresupuesto).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        jdbc.update("UPDATE Presupuestos SET id_estado=(SELECT id_estado FROM Estados_Factura WHERE descripcion='RECHAZADO') WHERE id_presupuesto=4001");
        consultar("1/informes/presupuestos-por-estado?desde="+fechaPresupuesto+"&hasta="+fechaPresupuesto).andExpect(status().isOk())
                .andExpect(jsonPath("$.estados[?(@.estado == 'RECHAZADO')].cantidad").value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void estadosRespetanEmpresaYDescartanRelacionesIncoherentes() throws Exception {
        asignar(2);
        var antesObras=leer("1/informes/obras-por-estado?desde=2025-01-01&hasta=2026-12-31").get("total").asLong();
        var ajenas=leer("2/informes/obras-por-estado?desde=2025-01-01&hasta=2026-12-31").get("total").asLong();
        assertThat(antesObras).isNotEqualTo(ajenas);
        jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_empresa=1 AND id_cliente=101");
        consultar("1/informes/obras-por-estado?desde=2025-01-01&hasta=2026-12-31").andExpect(jsonPath("$.total").value(0));
        consultar("1/informes/presupuestos-por-estado?desde=2025-01-01&hasta=2026-12-31").andExpect(jsonPath("$.total").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings={"OBRAS","PRESUPUESTOS"})
    void informeDeEstadoExigePermisoDeLaFuente(String permiso) throws Exception {
        retirar(permiso);
        String ruta=permiso.equals("OBRAS")?"obras-por-estado":"presupuestos-por-estado";
        consultar("1/informes/"+ruta+PERIODO).andExpect(status().isNotFound());
        String otro=permiso.equals("OBRAS")?"presupuestos-por-estado":"obras-por-estado";
        consultar("1/informes/"+otro+PERIODO).andExpect(status().isOk());
    }

    @Test
    void facturaSinObraSeCuentaSinExponerDatosPrivados() throws Exception {
        jdbc.update("UPDATE Facturas_Cliente SET id_obra = NULL, observaciones = 'PRIVADO' WHERE id_factura = 5001");
        var informe = leer("1/informes/facturacion-cliente" + PERIODO);
        assertThat(informe.get("totales").get("total").asText()).isEqualTo("4235.00");
        assertThat(informe.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("empresaId", "tipo", "desde", "hasta", "criterio", "meses", "totales");
        assertThat(informe.toString()).doesNotContain("PRIVADO", "observaciones", "iban", "numeroOperacion", "tercero");
    }

    @Test
    void agregacionSuperaUnaPaginaSinTruncarYConDecimalesExactos() throws Exception {
        for (int i = 0; i < 250; i++) {
            jdbc.update("INSERT INTO Facturas_Cliente (id_factura, numero, id_serie, fecha, subtotal, total, id_cliente) VALUES (?, ?, 103, '2026-12-01', 0.10, 0.10, 101)", 97000 + i, "DEMO-INFORME-" + i);
            jdbc.update("INSERT INTO API_Factura_Cliente_Empresa VALUES (?, 1)", 97000 + i);
            jdbc.update("INSERT INTO Cobros (id_factura, fecha, importe) VALUES (?, '2026-12-02', 0.03), (?, '2026-12-03', 0.02)", 97000 + i, 97000 + i);
        }
        String periodo = "?desde=2026-12-01&hasta=2026-12-31";
        consultar("1/informes/facturacion-cliente" + periodo).andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.facturas").value(250)).andExpect(jsonPath("$.totales.total").value("25.00"));
        consultar("1/informes/cobros" + periodo).andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.movimientos").value(500)).andExpect(jsonPath("$.totales.importe").value("12.50"));
        consultar("1/informes/saldos-cliente" + periodo).andExpect(status().isOk())
                .andExpect(jsonPath("$.totales.facturas").value(250)).andExpect(jsonPath("$.totales.saldoAritmetico").value("12.50"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"facturacion-cliente", "facturacion-proveedor", "cobros", "pagos", "saldos-cliente", "saldos-proveedor",
            "obras-por-estado","presupuestos-por-estado"})
    void sesionEmpresaYEscriturasProtegidas(String ruta) throws Exception {
        String path = "/api/v1/empresas/1/informes/" + ruta + PERIODO;
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        consultar("2/informes/" + ruta + PERIODO).andExpect(status().isNotFound());
        consultar("999999/informes/" + ruta + PERIODO).andExpect(status().isNotFound());
        jdbc.update("UPDATE API_Sesiones SET revocada = TRUE WHERE id_usuario = ?", usuario);
        consultar("1/informes/" + ruta + PERIODO).andExpect(status().isUnauthorized());
    }
}
