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
class ComprasTests extends PostgreSqlIntegrationTest {
    // Fixture sintético determinista: BBAN de prueba y checksum calculado localmente; no procede de una cuenta conocida.
    private static final String IBAN_SINTETICO = "GB05TEST00000000005432";
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
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-compras', ?)", passwords.encode("clave-ficticia-pruebas"));
        usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario = 'test-compras'", Integer.class);
        asignar(1);
        token = auth.login(new AuthDtos.Login("test-compras", "clave-ficticia-pruebas")).accessToken();
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
    void proveedorDetalladoProtegeIbanSinPermisoFinanciero() throws Exception {
        retirar("PAGOS");
        jdbc.update("UPDATE Proveedores SET iban = 'PRIVADO-IBAN' WHERE id_proveedor = 201");
        jdbc.update("UPDATE Entidades SET observaciones = 'PRIVADO', email = 'PRIVADO', nif_cif = 'PRIVADO' WHERE id_entidad = 201");
        var body = consultar("1/proveedores/201").andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(fixture("Suministros Taller [DEMO]", "Proveedor Ejemplo 001 SL")))
                .andExpect(jsonPath("$.tipo").value("EMPRESA")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "codigo", "nombre", "tipo", "activo", "nifCif", "telefono", "movil", "email", "observaciones", "ibanEnmascarado");
        assertThat(body).contains("PRIVADO").doesNotContain("PRIVADO-IBAN", "****IBAN");
        consultar("1/proveedores/201/contactos").andExpect(status().isOk());
        consultar("1/proveedores/201/direcciones").andExpect(status().isOk());
        var list = consultar("1/proveedores").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("PRIVADO");
        jdbc.update("UPDATE Pedidos_Proveedor SET observaciones = 'PRIVADO' WHERE id_pedido = 98001");
        var pedido = consultar("1/pedidos-proveedor/98001").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(pedido).doesNotContain("PRIVADO", "iban", "email", "nif", "observaciones", "recibido", "pendiente", "facturado");
        assertThat(json.readTree(pedido).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "numero", "fecha", "estado", "proveedor", "subtotal", "descuento", "impuestos", "total");
    }

    @Test
    void permisoDeTesoreriaSoloExponeIbanEnmascaradoEnElDetalle() throws Exception {
        jdbc.update("UPDATE Proveedores SET iban = ? WHERE id_proveedor = 201", IBAN_SINTETICO);
        jdbc.update("INSERT INTO Rol_Permiso(id_rol,id_permiso,nivel_acceso) SELECT r.id_rol,p.id_permiso,'LECTURA' FROM Roles r CROSS JOIN Permisos p WHERE r.nombre='CONSULTA_RECURSOS_DEMO' AND p.nombre='PAGOS' ON CONFLICT (id_rol,id_permiso) DO UPDATE SET nivel_acceso='LECTURA'");
        consultar("1/proveedores/201").andExpect(status().isOk())
                .andExpect(jsonPath("$.ibanEnmascarado").value("****5432"));
        var listado = consultar("1/proveedores").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(listado).doesNotContain("5432", "GB05TEST");
    }

    @Test
    void importesHistoricosDecimalesNoSeRecalculanConElCatalogo() throws Exception {
        consultar("1/pedidos-proveedor/98002").andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value("200.00")).andExpect(jsonPath("$.descuento").value("20.00"))
                .andExpect(jsonPath("$.impuestos").value("37.80")).andExpect(jsonPath("$.total").value("217.80"));
        jdbc.update("UPDATE Articulos SET precio_coste = 999, descripcion = 'CATALOGO ACTUAL' WHERE referencia = 'DEMO-ART-001'");
        jdbc.update("UPDATE Pedidos_Proveedor SET total = 999.99 WHERE id_pedido = 98001");
        consultar("1/pedidos-proveedor/98001").andExpect(status().isOk()).andExpect(jsonPath("$.total").value("999.99"));
        var linea = consultar("1/pedidos-proveedor/98001/lineas/98101").andExpect(status().isOk())
                .andExpect(jsonPath("$.descripcion").value(fixture("Perfiles encargados [DEMO]", "Dato sintético 043")))
                .andExpect(jsonPath("$.cantidad").value("2.00")).andExpect(jsonPath("$.precioCoste").value("100.00"))
                .andExpect(jsonPath("$.importe").value("200.00")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(linea).properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "pedidoId", "referenciaArticulo", "descripcion", "cantidad", "precioCoste", "descuento", "iva", "importe");
        var nullable = consultar("1/pedidos-proveedor/98002/lineas/98103").andExpect(status().isOk())
                .andExpect(jsonPath("$.descuento").value("10.00")).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(nullable).get("descripcion").isNull()).isTrue();
    }

    @Test
    void listasPaginadasFiltrosCombinadosYDesempateDeFechas() throws Exception {
        consultar("1/pedidos-proveedor?size=1&sort=numero,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items[0].id").value(98004));
        consultar("1/pedidos-proveedor?size=1&page=1&sort=numero,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(98002));
        consultar("1/pedidos-proveedor?proveedor=201&estado=ACEPTADO&desde=2026-09-15&hasta=2026-09-15&q=PP-002")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(98002));
        consultar("1/proveedores/201/pedidos?estado=PENDIENTE&hasta=2026-09-01").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(98001));
        consultar("1/pedidos-proveedor/98001/lineas?size=1&sort=id,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].id").value(98102));
        consultar("1/pedidos-proveedor/98001/lineas?size=1&page=1&sort=id,desc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(98101));
        jdbc.update("UPDATE Pedidos_Proveedor SET fecha = '2026-09-01' WHERE id_pedido = 98002");
        consultar("1/pedidos-proveedor?sort=fecha,asc&size=1&page=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(98002));
    }

    @Test
    void proveedoresOrdenadosYBusquedaLiteralSinInyeccion() throws Exception {
        consultar("1/proveedores?sort=codigo,desc&size=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].id").value(202));
        consultar("1/proveedores?q=" + fixture("Taller", "001") + "&sort=nombre,asc").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(201)).andExpect(jsonPath("$.totalElements").value(1));
        jdbc.update("UPDATE Entidades SET razon_social = 'Igual' WHERE id_entidad IN (201, 202)");
        consultar("1/proveedores?sort=nombre,asc&size=1&page=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(202));
        for (String path : new String[]{"proveedores", "pedidos-proveedor", "proveedores/201/pedidos"}) {
            for (String q : new String[]{"%", "_", "!", "' OR 1=1 --"}) {
                mvc.perform(get("/api/v1/empresas/1/" + path).param("q", q).header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            }
        }
    }

    @Test
    void listasVaciasNoConfundenRecursosAjenosConRecursosSinLineas() throws Exception {
        consultar("1/pedidos-proveedor/98004/lineas").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/pedidos-proveedor?proveedor=202").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/pedidos-proveedor?estado=INEXISTENTE").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/pedidos-proveedor?desde=2027-01-01").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/pedidos-proveedor?page=99").andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
        asignar(3);
        consultar("3/proveedores").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        consultar("3/pedidos-proveedor").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void bajasYProveedorAutonomoConservanElHistoricoYLosNulos() throws Exception {
        jdbc.update("UPDATE Proveedores SET tipo_proveedor = 'AUTONOMO' WHERE id_proveedor = 201");
        jdbc.update("UPDATE Entidades SET tipo_entidad = 'PERSONA', razon_social = NULL, nombre = 'Persona', apellidos = 'Ejemplo', activo = FALSE WHERE id_entidad = 201");
        jdbc.update("UPDATE Articulos SET activo = FALSE WHERE referencia = 'DEMO-ART-001'");
        consultar("1/proveedores/201").andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Persona Ejemplo"))
                .andExpect(jsonPath("$.activo").value(false)).andExpect(jsonPath("$.tipo").value("AUTONOMO"));
        consultar("1/pedidos-proveedor/98001").andExpect(status().isOk())
                .andExpect(jsonPath("$.proveedor.nombre").value("Persona Ejemplo"));
        consultar("1/pedidos-proveedor/98001/lineas").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        jdbc.update("UPDATE Entidades SET nombre = '', apellidos = NULL WHERE id_entidad = 201");
        var body = consultar("1/proveedores/201").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).get("nombre").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2/proveedores", "1/proveedores/999999",
            "1/pedidos-proveedor/98003", "1/pedidos-proveedor/999999", "1/pedidos-proveedor/98003/lineas",
            "1/pedidos-proveedor/98003/lineas/98104", "1/pedidos-proveedor/98001/lineas/98103", "1/pedidos-proveedor/98001/lineas/98104",
            "1/pedidos-proveedor/98001/lineas/999999"})
    void idsAjenosOConPadreIncorrectoDevuelven404(String path) throws Exception {
        consultar(path).andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void accesoADosEmpresasNoMezclaPedidosNiLineas() throws Exception {
        asignar(2);
        consultar("2/proveedores").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(201));
        consultar("2/pedidos-proveedor").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(98003));
        consultar("2/proveedores/202/pedidos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        consultar("2/pedidos-proveedor/98003/lineas/98104").andExpect(status().isOk()).andExpect(jsonPath("$.importe").value("30.00"));
        consultar("2/pedidos-proveedor/98001").andExpect(status().isNotFound());
        consultar("1/pedidos-proveedor/98001/lineas/98104").andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pedido-ajeno", "pedido-sin-asignar", "articulo-ajeno", "articulo-sin-asignar"})
    void pertenenciasIncoherentesOcultanElPedidoCompleto(String caso) throws Exception {
        switch (caso) {
            case "pedido-ajeno" -> jdbc.update("UPDATE API_Pedido_Proveedor_Empresa SET id_empresa = 2 WHERE id_pedido = 98001");
            case "pedido-sin-asignar" -> jdbc.update("DELETE FROM API_Pedido_Proveedor_Empresa WHERE id_pedido = 98001");
            case "articulo-ajeno" -> jdbc.update("UPDATE Lineas_Pedido SET referencia_articulo = 'DEMO-ART-003' WHERE id_linea = 98101");
            case "articulo-sin-asignar" -> jdbc.update("DELETE FROM API_Articulo_Empresa WHERE referencia_articulo = 'DEMO-ART-001'");
        }
        asignar(2);
        consultar("1/pedidos-proveedor/98001").andExpect(status().isNotFound());
        consultar("2/pedidos-proveedor/98001").andExpect(status().isNotFound());
        consultar("1/pedidos-proveedor/98001/lineas").andExpect(status().isNotFound());
        consultar("1/pedidos-proveedor/98001/lineas/98102").andExpect(status().isNotFound());
        consultar("1/pedidos-proveedor").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        consultar("1/proveedores/201/pedidos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void proveedorSinPertenenciaOcultaSuFichaYSusPedidos() throws Exception {
        jdbc.update("DELETE FROM API_Proveedor_Empresa WHERE id_proveedor = 201");
        consultar("1/proveedores/201").andExpect(status().isNotFound());
        consultar("1/proveedores/201/pedidos").andExpect(status().isNotFound());
        consultar("1/pedidos-proveedor").andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        consultar("1/pedidos-proveedor/98001/lineas").andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROVEEDORES", "PEDIDOS"})
    void cabecerasYLineasExigenAmbosPermisos(String permiso) throws Exception {
        retirar(permiso);
        for (String path : new String[]{"pedidos-proveedor", "pedidos-proveedor/98001", "proveedores/201/pedidos",
                "pedidos-proveedor/98001/lineas", "pedidos-proveedor/98001/lineas/98101"}) {
            consultar("1/" + path).andExpect(status().isNotFound());
        }
        consultar("1/proveedores").andExpect(permiso.equals("PROVEEDORES") ? status().isNotFound() : status().isOk());
    }

    @Test
    void lineasRequierenArticulosYLecturaFinancieraNoConcedeCompras() throws Exception {
        retirar("ARTICULOS");
        consultar("1/pedidos-proveedor/98001").andExpect(status().isOk());
        consultar("1/pedidos-proveedor/98001/lineas").andExpect(status().isNotFound());
        consultar("1/pedidos-proveedor/98001/lineas/98101").andExpect(status().isNotFound());
        retirar("PEDIDOS");
        consultar("1/facturas-proveedor/6001").andExpect(status().isOk());
        consultar("1/pedidos-proveedor").andExpect(status().isNotFound());
    }

    @Test
    void rolGlobalODeOtraEmpresaNoCompletaLosPermisos() throws Exception {
        jdbc.update("INSERT INTO Roles (nombre) VALUES ('COMPRAS_SOLO_PROVEEDORES')");
        int rol = jdbc.queryForObject("SELECT id_rol FROM Roles WHERE nombre = 'COMPRAS_SOLO_PROVEEDORES'", Integer.class);
        jdbc.update("INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso) SELECT ?, id_permiso, 'LECTURA' FROM Permisos WHERE nombre = 'PROVEEDORES'", rol);
        jdbc.update("UPDATE API_Accesos_Empresa SET id_rol = ? WHERE id_usuario = ? AND id_empresa = 1", rol, usuario);
        asignar(2);
        jdbc.update("INSERT INTO Usuario_Rol (id_usuario, id_rol) SELECT ?, id_rol FROM Roles WHERE nombre = 'CONSULTA_RECURSOS_DEMO'", usuario);
        consultar("1/proveedores").andExpect(status().isOk());
        consultar("1/pedidos-proveedor").andExpect(status().isNotFound());
        consultar("2/pedidos-proveedor").andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"proveedores?size=0", "proveedores?size=101", "proveedores?page=-1", "proveedores?page=100001",
            "proveedores?sort=nombre,ASC", "proveedores?q=a&q=b", "pedidos-proveedor?proveedor=0", "pedidos-proveedor?proveedor=no",
            "pedidos-proveedor?proveedor=201&proveedor=202", "pedidos-proveedor?desde=2026-02-30", "pedidos-proveedor?desde=2026-9-01",
            "pedidos-proveedor?desde=2026-10-01&hasta=2026-09-01", "pedidos-proveedor?sort=total,asc", "pedidos-proveedor?estado=A&estado=B",
            "pedidos-proveedor?obra=1001", "proveedores/201/pedidos?proveedor=201", "pedidos-proveedor/98001/lineas?q=Panel"})
    void parametrosInvalidosDevuelven400(String path) throws Exception {
        consultar("1/" + path).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }

    @Test
    void textosDemasiadoLargosYOrdenMaliciosoSeRechazan() throws Exception {
        for (String key : new String[]{"q", "estado", "sort"}) {
            String value = key.equals("q") ? "x".repeat(101) : key.equals("estado") ? "x".repeat(301) : "id,asc; DROP TABLE Proveedores";
            mvc.perform(get("/api/v1/empresas/1/pedidos-proveedor").param(key, value).header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void autenticacionEscriturasYAutorizacionAntesDeFiltros() throws Exception {
        for (String path : new String[]{"proveedores", "proveedores/201", "proveedores/201/pedidos", "pedidos-proveedor",
                "pedidos-proveedor/98001", "pedidos-proveedor/98001/lineas", "pedidos-proveedor/98001/lineas/98101"}) {
            mvc.perform(get("/api/v1/empresas/1/" + path)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/empresas/1/" + path).contentType("application/json").content("{}")
                    .header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        }
        jdbc.update("UPDATE API_Empresas SET activo = FALSE WHERE id_empresa = 1");
        consultar("1/pedidos-proveedor?size=0").andExpect(status().isNotFound());
        consultar("1/proveedores?size=0").andExpect(status().isNotFound());
        consultar("1/pedidos-proveedor/98001/lineas?size=0").andExpect(status().isNotFound());
    }
}
