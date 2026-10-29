package com.empresa.api;

import com.empresa.api.model.*;
import com.empresa.api.maestros.MaestroService;
import com.empresa.api.security.AuthDtos;
import com.empresa.api.security.AuthService;
import jakarta.persistence.EntityManager;
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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class MaestrosCompartidosTests extends PostgreSqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    @Autowired MaestroService service;
    @Autowired jakarta.validation.Validator validator;
    private int usuario;
    private String token;

    @BeforeEach void preparar() {
        usuario = jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES ('test-maestros',?) RETURNING id_usuario",
                Integer.class, passwords.encode("clave-ficticia"));
        for (int empresa : new int[]{1,2}) {
            jdbc.update("INSERT INTO API_Accesos_Empresa SELECT ?,?,id_rol FROM Roles WHERE nombre='ADMIN'", usuario, empresa);
        }
        token = auth.login(new AuthDtos.Login("test-maestros", "clave-ficticia")).accessToken();
    }

    private ResultActions leer(String path) throws Exception {
        return mvc.perform(get("/api/v1/empresas/" + path).header("Authorization", "Bearer " + token));
    }
    private ResultActions compartir(String path) throws Exception {
        return mvc.perform(put("/api/v1/empresas/" + path).header("Authorization", "Bearer " + token));
    }
    private String cuerpo(String path) throws Exception {
        return leer(path).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test void fichaFiscalExplicitaSinConfundirEmpresaConCliente() throws Exception {
        leer("1/ficha").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.datosFiscales.nifCif").value("DEMO-EMP-001"))
                .andExpect(jsonPath("$.datosFiscales.razonSocial")
                        .value(fixture("Dorna Metal [DEMO]", "Empresa Demostración 01 SL")))
                .andExpect(jsonPath("$.datosFiscales.observaciones").doesNotExist());
        assertThat(entities.find(Empresa.class, 1).getEntidad().getId()).isEqualTo(1);
        jdbc.update("UPDATE API_Empresas SET id_entidad=NULL WHERE id_empresa=2");
        assertThat(json.readTree(cuerpo("2/ficha")).get("datosFiscales").isNull()).isTrue();
        leer("3/ficha").andExpect(status().isNotFound());
    }

    @Test void compartirClienteEsIdempotenteComparteContactosYConservaPropiedad() throws Exception {
        String obras = cuerpo("1/obras"), facturas = cuerpo("1/facturas-cliente"), informes = cuerpo("1/informes/facturacion-cliente?desde=2026-01-01&hasta=2026-12-31");
        jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_cliente=101 AND id_empresa=2");
        leer("2/clientes/101").andExpect(status().isNotFound());
        compartir("1/clientes/101/visibilidad/2").andExpect(status().isNoContent()).andExpect(content().string(""));
        compartir("1/clientes/101/visibilidad/2").andExpect(status().isNoContent());
        compartir("2/clientes/101/visibilidad/2").andExpect(status().isNoContent());
        entities.clear();
        assertThat(entities.find(Cliente.class, 101).getEmpresasVisibles()).extracting(Empresa::getId).containsExactlyInAnyOrder(1,2,3,4);
        leer("2/clientes/101").andExpect(status().isOk());
        leer("2/clientes/101/contactos").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(101));
        leer("2/clientes/101/direcciones").andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(101));
        leer("2/clientes?size=1").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        for (String path : new String[]{"2/obras/1001", "2/facturas-cliente/5001", "2/obras/1001/documentos"}) {
            leer(path).andExpect(status().isNotFound());
        }
        assertThat(cuerpo("1/obras")).isEqualTo(obras);
        assertThat(cuerpo("1/facturas-cliente")).isEqualTo(facturas);
        assertThat(cuerpo("1/informes/facturacion-cliente?desde=2026-01-01&hasta=2026-12-31")).isEqualTo(informes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Visibilidad_Auditoria WHERE id_cliente=101 AND id_usuario=? AND empresa_origen=1 AND empresa_destino=2", Integer.class, usuario)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT id_empresa FROM API_Obra_Empresa WHERE id_obra=1001", Integer.class)).isEqualTo(1);
        assertThat(entities.find(Obra.class, 1001).getEmpresa().getId()).isEqualTo(1);
        assertThat(entities.find(FacturaCliente.class, 5001).getEmpresa().getId()).isEqualTo(1);
    }

    @Test void compartirProveedorNoCompartePedidosFacturasNiPagos() throws Exception {
        String pedidos = cuerpo("1/pedidos-proveedor"), facturas = cuerpo("1/facturas-proveedor"), pagos = cuerpo("1/pagos");
        jdbc.update("DELETE FROM API_Proveedor_Empresa WHERE id_proveedor=201 AND id_empresa=2");
        compartir("1/proveedores/201/visibilidad/2").andExpect(status().isNoContent());
        compartir("1/proveedores/201/visibilidad/2").andExpect(status().isNoContent());
        entities.clear();
        assertThat(entities.find(Proveedor.class, 201).getEmpresasVisibles()).extracting(Empresa::getId).containsExactlyInAnyOrder(1,2,3,4);
        leer("2/proveedores/201").andExpect(status().isOk()).andExpect(jsonPath("$.iban").doesNotExist());
        leer("2/proveedores/201/pedidos").andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        leer("2/facturas-proveedor/6001").andExpect(status().isNotFound());
        assertThat(cuerpo("1/pedidos-proveedor")).isEqualTo(pedidos);
        assertThat(cuerpo("1/facturas-proveedor")).isEqualTo(facturas);
        assertThat(cuerpo("1/pagos")).isEqualTo(pagos);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Visibilidad_Auditoria WHERE id_proveedor=201", Integer.class)).isEqualTo(1);
    }

    @Test void articuloCompartidoNoDuplicaStockNiExponeMovimientosAjenos() throws Exception {
        String stock = cuerpo("1/almacenes/101/stock"), movimientos = cuerpo("1/movimientos-stock");
        var articulo = entities.find(Articulo.class, "DEMO-ART-001");
        articulo.getEmpresasVisibles().add(entities.find(Empresa.class, 2));
        entities.flush(); entities.clear();
        leer("2/articulos/DEMO-ART-001").andExpect(status().isOk());
        leer("2/movimientos-stock/97001").andExpect(status().isNotFound());
        leer("2/almacenes/101/stock").andExpect(status().isNotFound());
        assertThat(cuerpo("1/almacenes/101/stock")).isEqualTo(stock);
        assertThat(cuerpo("1/movimientos-stock")).isEqualTo(movimientos);
    }

    @ParameterizedTest @ValueSource(strings={"1/clientes/999999/visibilidad/2",
            "1/clientes/101/visibilidad/3", "3/clientes/101/visibilidad/2", "1/clientes/101/visibilidad/999999",
            "1/proveedores/999999/visibilidad/2", "1/proveedores/201/visibilidad/3"})
    void rechazaOrigenDestinoORecursoNoAutorizadoSinAuditoria(String path) throws Exception {
        compartir(path).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Visibilidad_Auditoria", Integer.class)).isZero();
    }

    @ParameterizedTest @ValueSource(ints={1,2})
    void requiereEscrituraEnAmbasEmpresasAunquePermitaLeer(int empresa) throws Exception {
        jdbc.update("UPDATE API_Accesos_Empresa SET id_rol=(SELECT id_rol FROM Roles WHERE nombre='SOCIO') WHERE id_usuario=? AND id_empresa=?", usuario, empresa);
        leer(empresa + "/ficha").andExpect(status().isOk());
        compartir("1/clientes/101/visibilidad/2").andExpect(status().isNotFound());
        compartir("1/proveedores/201/visibilidad/2").andExpect(status().isNotFound());
    }

    @ParameterizedTest @ValueSource(ints={1,2})
    void empresaInactivaImpideCompartir(int empresa) throws Exception {
        jdbc.update("UPDATE API_Empresas SET activo=false WHERE id_empresa=?", empresa);
        compartir("1/clientes/101/visibilidad/2").andExpect(status().isNotFound());
    }

    @Test void permisosRevocadosSeAplicanConElMismoToken() throws Exception {
        jdbc.update("DELETE FROM Rol_Permiso WHERE id_rol=(SELECT id_rol FROM Roles WHERE nombre='ADMIN') AND id_permiso=(SELECT id_permiso FROM Permisos WHERE nombre='CLIENTES')");
        compartir("1/clientes/101/visibilidad/2").andExpect(status().isNotFound());
    }

    @Test void compartirExigeAutenticacion() throws Exception {
        mvc.perform(put("/api/v1/empresas/1/clientes/101/visibilidad/2")).andExpect(status().isUnauthorized());
    }

    @Test void escrituraJpaCreaAsignacionObligatoriaYNoTransfierePropiedad() {
        var almacen = new Almacen();
        almacen.setCodigoAlmacen("TEST-MAESTROS");
        almacen.setDescripcion("Almacen de prueba");
        almacen.setActivo(true);
        almacen.setFechaCreacion(java.time.LocalDateTime.now());
        almacen.setFechaModificacion(java.time.LocalDateTime.now());
        assertThat(validator.validate(almacen)).extracting(v -> v.getPropertyPath().toString()).containsExactly("empresa");
        almacen.setEmpresa(entities.find(Empresa.class, 2));
        entities.persist(almacen); entities.flush();
        int id = almacen.getId();
        assertThat(jdbc.queryForObject("SELECT id_empresa FROM API_Almacen_Empresa WHERE id_almacen=?", Integer.class, id)).isEqualTo(2);
        almacen.setEmpresa(entities.find(Empresa.class, 1));
        entities.flush(); entities.clear();
        assertThat(entities.find(Almacen.class, id).getEmpresa().getId()).isEqualTo(2);
    }
}
