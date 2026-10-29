package com.empresa.api;

import com.empresa.api.facturacion.FacturacionService;
import com.empresa.api.security.AuthDtos;
import com.empresa.api.security.AuthService;
import com.empresa.api.tesoreria.PrevisionService;
import com.empresa.api.tesoreria.TesoreriaService;
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

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.empresa.api.facturacion.FacturacionDtos.*;
import static com.empresa.api.tesoreria.TesoreriaDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class PrevisionTests extends PostgreSqlIntegrationTest {
    @Autowired FacturacionService facturacion;
    @Autowired TesoreriaService tesoreria;
    @Autowired PrevisionService prevision;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;
    private int admin;
    private String token;
    private LocalDate hoy;

    @BeforeEach
    void preparar() {
        hoy=LocalDate.now(clock);
        jdbc.update("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES ('test-prevision',?)", passwords.encode("clave-prevision-prueba"));
        admin=jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario='test-prevision'",Integer.class);
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",admin);
        token=auth.login(new AuthDtos.Login("test-prevision","clave-prevision-prueba")).accessToken();
    }

    private Detalle factura(Tipo tipo) {
        String clave=UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        int serie=facturacion.crearSerie(admin,1,tipo,new NuevaSerie(clave,clave,hoy.getYear(),6,false)).id();
        var documento=facturacion.crear(admin,1,tipo,new Nuevo(serie,tipo==Tipo.CLIENTE?101:201,null,
                hoy,hoy.plusDays(30),tipo==Tipo.PROVEEDOR?clave:null,
                List.of(new EntradaLinea("Dato privado","1","100","0","0"))));
        return facturacion.emitir(admin,1,tipo,documento.id());
    }

    private int vencimiento(Tipo tipo) {
        var documento=factura(tipo);
        return tesoreria.planificar(admin,1,tipo,documento.id(),
                new Plan(List.of(new Cuota(hoy.plusDays(30),"100")))).getFirst().id();
    }

    private ResultActions consultar(String query) throws Exception {
        return mvc.perform(get("/api/v1/empresas/1/tesoreria/prevision"+query)
                .header("Authorization","Bearer "+token));
    }

    private String periodo() {
        return "?desde="+hoy.minusDays(1)+"&hasta="+hoy.plusMonths(2);
    }

    @Test
    void agregaPendientesDeClienteYProveedorYNoExponeDatosPrivados() throws Exception {
        int cliente=vencimiento(Tipo.CLIENTE);
        vencimiento(Tipo.PROVEEDOR);
        tesoreria.registrar(admin,1,Tipo.CLIENTE,new NuevoMovimiento(UUID.randomUUID().toString(),hoy,"25","TRANSFERENCIA",
                List.of(new Reparto(cliente,"25"))));

        JsonNode resultado=json.readTree(consultar(periodo()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(resultado.get("totalRegistros").asLong()).isEqualTo(2);
        assertThat(resultado.get("saldoFirmadoTotal").asText()).isEqualTo("175.00");
        assertThat(resultado.get("porCobrarFirmado").asText()).isEqualTo("75.00");
        assertThat(resultado.get("porPagarFirmado").asText()).isEqualTo("100.00");
        assertThat(resultado.get("items")).allSatisfy(item -> {
            assertThat(item.get("estadoTemporal").asText()).isEqualTo("PENDIENTE");
            assertThat(item.properties()).extracting(java.util.Map.Entry::getKey).containsExactlyInAnyOrder(
                    "id","documentoId","facturaId","terceroId","tipo","posicion","fecha",
                    "importe","aplicado","saldoFirmado","estadoTemporal");
        });
        assertThat(resultado.toString()).doesNotContain("Dato privado","terceroNombre","terceroNif","autorId");
    }

    @Test
    void movimientosRevertidosRestauranElPendiente() throws Exception {
        int vencimiento=vencimiento(Tipo.CLIENTE);
        var movimiento=tesoreria.registrar(admin,1,Tipo.CLIENTE,new NuevoMovimiento(UUID.randomUUID().toString(),hoy,"40","TRANSFERENCIA",
                List.of(new Reparto(vencimiento,"40"))));
        consultar(periodo()+"&tipo=CLIENTE").andExpect(jsonPath("$.saldoFirmadoTotal").value("60.00"));
        tesoreria.revertir(admin,1,Tipo.CLIENTE,movimiento.id(),new Revertir(hoy,"Corrección"));
        consultar(periodo()+"&tipo=CLIENTE").andExpect(jsonPath("$.saldoFirmadoTotal").value("100.00"))
                .andExpect(jsonPath("$.items[0].aplicado").value("0.00"));
    }

    @Test
    void distingueVencidosYRectificativasComoAjustes() throws Exception {
        int normal=vencimiento(Tipo.CLIENTE);
        jdbc.update("UPDATE API_Vencimientos SET fecha=? WHERE id=?",hoy.minusDays(1),normal);
        var original=factura(Tipo.CLIENTE);
        String clave=UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        int serie=facturacion.crearSerie(admin,1,Tipo.CLIENTE,new NuevaSerie("Rect "+clave,"R"+clave,hoy.getYear(),6,true)).id();
        var rectificada=facturacion.rectificar(admin,1,Tipo.CLIENTE,original.id(),new Rectificar(serie,hoy,null,"Corrección"));
        facturacion.emitir(admin,1,Tipo.CLIENTE,rectificada.id());
        tesoreria.planificar(admin,1,Tipo.CLIENTE,rectificada.id(),new Plan(List.of(new Cuota(hoy.plusDays(1),"-100"))));

        JsonNode items=json.readTree(consultar("?desde="+hoy.minusDays(2)+"&hasta="+hoy.plusDays(2)+"&tipo=CLIENTE")
                .andReturn().getResponse().getContentAsString()).get("items");
        assertThat(items).anySatisfy(item -> assertThat(item.get("estadoTemporal").asText()).isEqualTo("VENCIDO"));
        assertThat(items).anySatisfy(item -> {
            assertThat(item.get("estadoTemporal").asText()).isEqualTo("AJUSTE");
            assertThat(item.get("saldoFirmado").asText()).isEqualTo("-100.00");
        });
    }

    @Test
    void filtraPorTipoYPaginaSinTruncarTotales() throws Exception {
        vencimiento(Tipo.CLIENTE);
        vencimiento(Tipo.PROVEEDOR);
        consultar(periodo()+"&tipo=CLIENTE&size=1&page=0").andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CLIENTE"))
                .andExpect(jsonPath("$.totalRegistros").value(1))
                .andExpect(jsonPath("$.porCobrarFirmado").value("100.00"))
                .andExpect(jsonPath("$.porPagarFirmado").value("0.00"))
                .andExpect(jsonPath("$.items.length()").value(1));
        consultar(periodo()+"&size=1&page=1").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegistros").value(2))
                .andExpect(jsonPath("$.saldoFirmadoTotal").value("200.00"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings={"FACTURAS_CLIENTE","FACTURAS_PROVEEDOR","COBROS","PAGOS","INFORMES"})
    void exigeLosPermisosSolicitados(String permiso) throws Exception {
        jdbc.update("DELETE FROM Rol_Permiso WHERE id_rol=(SELECT id_rol FROM Roles WHERE nombre='ADMIN') " +
                "AND id_permiso=(SELECT id_permiso FROM Permisos WHERE nombre=?)",permiso);
        consultar(periodo()).andExpect(status().isNotFound());
    }

    @Test
    void unTipoNoExigePermisosDeLaOtraCategoria() throws Exception {
        jdbc.update("DELETE FROM Rol_Permiso WHERE id_rol=(SELECT id_rol FROM Roles WHERE nombre='ADMIN') " +
                "AND id_permiso IN (SELECT id_permiso FROM Permisos WHERE nombre IN ('FACTURAS_PROVEEDOR','PAGOS'))");
        consultar(periodo()+"&tipo=CLIENTE").andExpect(status().isOk());
        consultar(periodo()+"&tipo=PROVEEDOR").andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings={"","?desde=2026-01-01","?hasta=2026-02-01","?desde=0000-01-01&hasta=1000-01-01",
            "?desde=2026-02-30&hasta=2026-03-01","?desde=2026-04-01&hasta=2026-03-01",
            "?desde=2024-01-31&hasta=2026-01-01","?desde=2026-01-01&hasta=2026-02-01&tipo=cliente",
            "?desde=2026-01-01&hasta=2026-02-01&page=-1","?desde=2026-01-01&hasta=2026-02-01&size=101",
            "?desde=2026-01-01&hasta=2026-02-01&otro=1","?desde=2026-01-01&desde=2026-01-02&hasta=2026-02-01"})
    void validaFechasPaginacionTipoYParametros(String query) throws Exception {
        consultar(query).andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos").isNotEmpty());
    }

    @Test
    void admiteElLimiteDeVeinticuatroMesesNaturales() throws Exception {
        consultar("?desde=2024-02-29&hasta=2026-01-31").andExpect(status().isOk());
        consultar("?desde=1000-01-01&hasta=1000-01-01").andExpect(status().isOk());
        consultar("?desde=9999-12-31&hasta=9999-12-31").andExpect(status().isOk());
    }

    @Test
    void protegeEmpresaSesionYMetodo() throws Exception {
        mvc.perform(get("/api/v1/empresas/1/tesoreria/prevision"+periodo())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/empresas/1/tesoreria/prevision"+periodo())
                .header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/empresas/999999/tesoreria/prevision"+periodo())
                .header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        jdbc.update("UPDATE API_Sesiones SET revocada=TRUE WHERE id_usuario=?",admin);
        consultar(periodo()).andExpect(status().isUnauthorized());
    }
}
