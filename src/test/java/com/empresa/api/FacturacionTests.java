package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.facturacion.*;
import com.empresa.api.security.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static com.empresa.api.facturacion.FacturacionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class FacturacionTests extends PostgreSqlIntegrationTest {
    @Autowired FacturacionService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;
    int admin;
    String token;
    LocalDate hoy;
    @BeforeEach void preparar() {
        hoy=LocalDate.now(clock);
        admin=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES ('test-fact-admin',?) RETURNING id_usuario",Integer.class,passwords.encode("clave-facturacion-prueba"));
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",admin);
        token=auth.login(new AuthDtos.Login("test-fact-admin","clave-facturacion-prueba")).accessToken();
    }
    Serie serie(Tipo tipo,boolean rect) { return service.crearSerie(admin,1,tipo,new NuevaSerie(rect?"Rectificativas":"Ordinarias",rect?"R-":"F-",hoy.getYear(),6,rect)); }
    Nuevo entrada(Tipo tipo,int serie) { return new Nuevo(serie,tipo==Tipo.CLIENTE?101:201,tipo==Tipo.CLIENTE?1001:null,hoy,hoy.plusDays(30),tipo==Tipo.PROVEEDOR?"EXT-2026-1":null,List.of(new EntradaLinea("Servicio","3.00","0.05","10.00","21.00"))); }
    Detalle crear(Tipo tipo) { return service.crear(admin,1,tipo,entrada(tipo,serie(tipo,false).id())); }
    String base(Tipo tipo) { return "/api/v1/empresas/1/facturacion/"+tipo; }
    void conflicto(Runnable r) { assertThatThrownBy(r::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(409)); }

    @ParameterizedTest @EnumSource(Tipo.class)
    void emisionReutilizaConsultasImportesExactosYEsIdempotente(Tipo tipo) throws Exception {
        String tabla=tipo==Tipo.CLIENTE?"Facturas_Cliente":"Facturas_Proveedor";
        int antes=jdbc.queryForObject("SELECT count(*) FROM "+tabla,Integer.class);
        var d=crear(tipo);
        assertThat(d.empresaId()).isEqualTo(1); assertThat(d.estado()).isEqualTo("BORRADOR"); assertThat(d.facturaId()).isNull(); assertThat(d.numero()).isNull();
        assertThat(d.subtotal()).isEqualTo("0.15"); assertThat(d.descuento()).isEqualTo("0.02"); assertThat(d.impuestos()).isEqualTo("0.03"); assertThat(d.total()).isEqualTo("0.16");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM "+tabla,Integer.class)).isEqualTo(antes);
        var emitida=service.emitir(admin,1,tipo,d.id());
        assertThat(emitida.numero()).isEqualTo("F-000001"); assertThat(emitida.facturaId()).isNotNull(); assertThat(emitida.emitido()).isNotNull();
        assertThat(service.emitir(admin,1,tipo,d.id())).isEqualTo(emitida);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM "+tabla,Integer.class)).isEqualTo(antes+1);
        assertThat(jdbc.queryForObject("SELECT siguiente_numero FROM Series_Documentos WHERE id_serie=?",Integer.class,d.serieId())).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT accion FROM API_Facturacion_Auditoria WHERE documento=? ORDER BY id",String.class,d.id())).containsExactly("CREAR","EMITIR");
        String path="/api/v1/empresas/1/facturas-"+tipo.name().toLowerCase(Locale.ROOT)+"/"+emitida.facturaId();
        mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value("0.16")).andExpect(jsonPath("$.numero").value("F-000001"));
        mvc.perform(get(path+"/lineas").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].importe").value("0.16"));
    }
    @ParameterizedTest @EnumSource(Tipo.class)
    void rectificacionTotalConservaOriginalYCompensaSinRecalcular(Tipo tipo) {
        var d=crear(tipo); var original=service.emitir(admin,1,tipo,d.id()); int r=serie(tipo,true).id();
        var rect=service.rectificar(admin,1,tipo,d.id(),new Rectificar(r,hoy,tipo==Tipo.PROVEEDOR?"ABONO-1":null,"Correccion total"));
        assertThat(rect.originalId()).isEqualTo(d.id()); assertThat(rect.total()).isEqualTo("-0.16");
        assertThat(rect.lineas().getFirst().cantidad()).isEqualTo("-3.00"); assertThat(rect.empresaNif()).isEqualTo(original.empresaNif());
        conflicto(()->service.rectificar(admin,1,tipo,d.id(),new Rectificar(r,hoy,null,"Duplicada")));
        var emitida=service.emitir(admin,1,tipo,rect.id());
        assertThat(emitida.numero()).isEqualTo("R-000001"); assertThat(service.consultar(admin,1,tipo,d.id()).estado()).isEqualTo("RECTIFICADA");
        String tabla=tipo==Tipo.CLIENTE?"Facturas_Cliente":"Facturas_Proveedor";
        assertThat(jdbc.queryForObject("SELECT sum(total) FROM "+tabla+" WHERE id_factura IN (?,?)",java.math.BigDecimal.class,original.facturaId(),emitida.facturaId())).isEqualByComparingTo("0.00");
        assertThat(service.emitir(admin,1,tipo,rect.id())).isEqualTo(emitida);
        conflicto(()->service.rectificar(admin,1,tipo,rect.id(),new Rectificar(r,hoy,null,"Cadena")));
        conflicto(()->service.anular(admin,1,tipo,d.id(),new Anular("No permitido")));
    }
    @Test void anularBorradorNoConsumeNumeroYConservaMotivosDeRectificacion() {
        var d=crear(Tipo.CLIENTE); service.emitir(admin,1,Tipo.CLIENTE,d.id()); int r=serie(Tipo.CLIENTE,true).id();
        var rect=service.rectificar(admin,1,Tipo.CLIENTE,d.id(),new Rectificar(r,hoy,null,"Error de contenido"));
        service.anular(admin,1,Tipo.CLIENTE,rect.id(),new Anular("Descartado"));
        assertThat(jdbc.queryForList("SELECT motivo FROM API_Facturacion_Auditoria WHERE documento=? ORDER BY id",String.class,rect.id())).containsExactly("Error de contenido","Descartado");
        assertThat(service.rectificar(admin,1,Tipo.CLIENTE,d.id(),new Rectificar(r,hoy,null,"Nueva correccion")).id()).isNotEqualTo(rect.id());
    }
    @Test void anulacionEsIdempotenteYNoPermiteEmitirNiCambiarMotivo() {
        var d=crear(Tipo.CLIENTE); var a=service.anular(admin,1,Tipo.CLIENTE,d.id(),new Anular("Descartado"));
        assertThat(service.anular(admin,1,Tipo.CLIENTE,d.id(),new Anular(" Descartado "))).isEqualTo(a);
        conflicto(()->service.anular(admin,1,Tipo.CLIENTE,d.id(),new Anular("Otro")));
        conflicto(()->service.emitir(admin,1,Tipo.CLIENTE,d.id()));
        assertThat(jdbc.queryForObject("SELECT siguiente_numero FROM Series_Documentos WHERE id_serie=?",Integer.class,d.serieId())).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"-1","0","0.001","1e2","100000000","NaN"," 1"})
    void cantidadesInvalidasSeRechazan(String cantidad) {
        assertThatThrownBy(()->CalculoFactura.linea(new EntradaLinea("Linea",cantidad,"1","0","21"),1)).isInstanceOf(ApiException.class);
    }
    @Test void calculoLimitaPorcentajesYDesbordamientosYRedondeaCadaLinea() {
        assertThatThrownBy(()->CalculoFactura.linea(new EntradaLinea("L","1","1","100.01","21"),1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->CalculoFactura.linea(new EntradaLinea("L","1","1","0","100.01"),1)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->CalculoFactura.linea(new EntradaLinea("L","99999999.99","99999999.99","0","21"),1)).isInstanceOf(ApiException.class);
        var l=CalculoFactura.linea(new EntradaLinea("L","1","0.05","0","10"),1);
        assertThat(l.getCuota()).isEqualByComparingTo("0.01"); assertThat(l.getTotal()).isEqualByComparingTo("0.06");
        var cero=CalculoFactura.linea(new EntradaLinea("L","1","5","100","21"),1); assertThat(cero.getTotal()).isZero();
    }
    @Test void noSeAdmitenTotalesAutoresNiCamposAnidadosAdicionales() throws Exception {
        int serie=serie(Tipo.CLIENTE,false).id();
        for(String extra:new String[]{",\"total\":\"1.00\"",",\"autorId\":1",",\"estado\":\"EMITIDA\""}) {
            String body=json.writeValueAsString(entrada(Tipo.CLIENTE,serie)); body=body.substring(0,body.length()-1)+extra+"}";
            mvc.perform(post(base(Tipo.CLIENTE)+"/documentos").header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        String body=json.writeValueAsString(entrada(Tipo.CLIENTE,serie)).replace("\"descripcion\":", "\"importe\":\"0.01\",\"descripcion\":");
        mvc.perform(post(base(Tipo.CLIENTE)+"/documentos").header("Authorization","Bearer "+token).contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={"SOCIO","LECTURA_EMPRESA","TRABAJADOR"})
    void rolesSinEscrituraNoCreanNiEmiten(String rol) throws Exception {
        var d=crear(Tipo.CLIENTE);
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",admin);
        if(rol.equals("SOCIO")) jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre=?",admin,rol);
        else jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) SELECT ?,1,id_rol FROM Roles WHERE nombre=?",admin,rol);
        mvc.perform(post(base(Tipo.CLIENTE)+"/documentos").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(entrada(Tipo.CLIENTE,d.serieId())))).andExpect(status().isNotFound());
        mvc.perform(put(base(Tipo.CLIENTE)+"/documentos/"+d.id()+"/emision").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        mvc.perform(get(base(Tipo.CLIENTE)+"/documentos/"+d.id()).header("Authorization","Bearer "+token)).andExpect(rol.equals("TRABAJADOR")?status().isNotFound():status().isOk());
    }
    @Test void empresaTipoTerceroYObraNoSePuedenCruzar() throws Exception {
        var d=crear(Tipo.CLIENTE);
        for(String path:new String[]{"/api/v1/empresas/2/facturacion/CLIENTE/documentos/"+d.id(),base(Tipo.PROVEEDOR)+"/documentos/"+d.id()})
            mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        for(Nuevo n:List.of(new Nuevo(d.serieId(),101,2001,hoy,null,null,entrada(Tipo.CLIENTE,d.serieId()).lineas())))
            mvc.perform(post(base(Tipo.CLIENTE)+"/documentos").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(n))).andExpect(status().isNotFound());
        mvc.perform(get(base(Tipo.CLIENTE)+"/documentos")).andExpect(status().isUnauthorized());
    }
    @Test void seriesSeSeparanPorEmpresaYExigenNaturalezaCorrecta() {
        var s=serie(Tipo.CLIENTE,false);
        service.crearSerie(admin,2,Tipo.CLIENTE,new NuevaSerie(s.nombre(),s.prefijo(),hoy.getYear(),6,false));
        conflicto(()->serie(Tipo.CLIENTE,false));
        int rect=serie(Tipo.CLIENTE,true).id();
        conflicto(()->service.crear(admin,1,Tipo.CLIENTE,entrada(Tipo.CLIENTE,rect)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Series_Documentos WHERE id_empresa=2 AND nombre=?",Integer.class,s.nombre())).isEqualTo(1);
    }
    @Test void fechaNoRetrocedeEnSerieYFalloNoConsumeNumero() {
        var d=crear(Tipo.CLIENTE); service.emitir(admin,1,Tipo.CLIENTE,d.id());
        var entrada=entrada(Tipo.CLIENTE,d.serieId());
        var anterior=service.crear(admin,1,Tipo.CLIENTE,new Nuevo(d.serieId(),101,null,hoy.minusDays(1),null,null,entrada.lineas()));
        conflicto(()->service.emitir(admin,1,Tipo.CLIENTE,anterior.id()));
        assertThat(jdbc.queryForObject("SELECT siguiente_numero FROM Series_Documentos WHERE id_serie=?",Integer.class,d.serieId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Facturacion_Auditoria WHERE documento=? AND accion='EMITIR'",Integer.class,anterior.id())).isZero();
    }
    @Test void referenciaProveedorNoSeDuplicaYSeLiberaAlAnularBorrador() {
        var d=crear(Tipo.PROVEEDOR);
        conflicto(()->service.crear(admin,1,Tipo.PROVEEDOR,entrada(Tipo.PROVEEDOR,d.serieId())));
        service.anular(admin,1,Tipo.PROVEEDOR,d.id(),new Anular("Duplicado detectado"));
        assertThat(service.crear(admin,1,Tipo.PROVEEDOR,entrada(Tipo.PROVEEDOR,d.serieId())).id()).isNotEqualTo(d.id());
    }
    @Test void identidadSeFijaAlEmitirYOriginalNoDependeDeCambiosDelMaestro() {
        var d=crear(Tipo.CLIENTE);
        jdbc.update("UPDATE Entidades SET razon_social='Nombre en emision' WHERE id_entidad=(SELECT id_entidad FROM Clientes WHERE id_cliente=101)");
        var emitida=service.emitir(admin,1,Tipo.CLIENTE,d.id()); assertThat(emitida.terceroNombre()).isEqualTo("Nombre en emision");
        jdbc.update("UPDATE Entidades SET razon_social='Nombre posterior' WHERE id_entidad=(SELECT id_entidad FROM Clientes WHERE id_cliente=101)");
        assertThat(service.consultar(admin,1,Tipo.CLIENTE,d.id()).terceroNombre()).isEqualTo("Nombre en emision");
    }
    @Test void paginasSonAcotadasYNoRevelanIdentidadFiscal() throws Exception {
        crear(Tipo.CLIENTE);
        mvc.perform(get(base(Tipo.CLIENTE)+"/documentos?size=1").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].empresaNif").doesNotExist());
        for(String q:new String[]{"size=0","size=101","page=100001","page=0&page=1","autor=1"})
            mvc.perform(get(base(Tipo.CLIENTE)+"/documentos?"+q).header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
    }
}
