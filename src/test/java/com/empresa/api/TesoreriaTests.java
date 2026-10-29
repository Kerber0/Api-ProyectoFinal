package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.facturacion.FacturacionService;
import com.empresa.api.security.*;
import com.empresa.api.tesoreria.*;
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
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.empresa.api.facturacion.FacturacionDtos.*;
import static com.empresa.api.tesoreria.TesoreriaDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class TesoreriaTests extends PostgreSqlIntegrationTest {
    @Autowired TesoreriaService service;
    @Autowired FacturacionService facturacion;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;
    int admin; String token; LocalDate hoy;
    @BeforeEach void preparar() {
        hoy=LocalDate.now(clock);
        admin=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES ('test-tes-admin',?) RETURNING id_usuario",Integer.class,passwords.encode("clave-tesoreria-prueba"));
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",admin);
        token=auth.login(new AuthDtos.Login("test-tes-admin","clave-tesoreria-prueba")).accessToken();
    }
    Detalle factura(Tipo tipo) { return factura(tipo,tipo==Tipo.CLIENTE?101:201); }
    Detalle factura(Tipo tipo,int tercero) {
        String key=UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        int serie=facturacion.crearSerie(admin,1,tipo,new NuevaSerie(key,key,hoy.getYear(),6,false)).id();
        var d=facturacion.crear(admin,1,tipo,new Nuevo(serie,tercero,null,hoy,hoy.plusDays(30),tipo==Tipo.PROVEEDOR?key:null,List.of(new EntradaLinea("Servicio","1","100","0","0"))));
        return facturacion.emitir(admin,1,tipo,d.id());
    }
    Plan plan(String... importes) { var cuotas=new ArrayList<Cuota>(); for(int i=0;i<importes.length;i++) cuotas.add(new Cuota(hoy.plusDays(30L*(i+1)),importes[i])); return new Plan(cuotas); }
    NuevoMovimiento entrada(String importe,Reparto... reparto) { return new NuevoMovimiento(UUID.randomUUID().toString(),hoy,importe,"TRANSFERENCIA",List.of(reparto)); }
    String base(Tipo tipo) { return "/api/v1/empresas/1/tesoreria/"+tipo; }
    void error(int codigo,Runnable action) { assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(codigo)); }

    @ParameterizedTest @EnumSource(Tipo.class)
    void cicloCompletoConservaConsultasEconomicasYReversion(Tipo tipo) throws Exception {
        var d=factura(tipo); var p=plan("40","60"); var cuotas=service.planificar(admin,1,tipo,d.id(),p);
        assertThat(service.planificar(admin,1,tipo,d.id(),p)).isEqualTo(cuotas);
        var request=entrada("70",new Reparto(cuotas.get(0).id(),"40"),new Reparto(cuotas.get(1).id(),"30"));
        var m=service.registrar(admin,1,tipo,request);
        assertThat(m.aplicaciones()).hasSize(2); assertThat(service.registrar(admin,1,tipo,request)).isEqualTo(m);
        var q=new LinkedMultiValueMap<String,String>(); q.add("documento",Integer.toString(d.id()));
        var pendientes=service.vencimientos(admin,1,tipo,q);
        assertThat(pendientes.totalElements()).isEqualTo(1); assertThat(pendientes.items().getFirst().pendiente()).isEqualTo("30.00");
        String economico="/api/v1/empresas/1/facturas-"+tipo.name().toLowerCase(Locale.ROOT)+"/"+d.facturaId();
        mvc.perform(get(economico+"/saldo").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.saldoAritmetico").value("30.00"));
        var conciliado=service.conciliar(admin,1,tipo,m.id(),new Conciliar("Banco-001"));
        assertThat(conciliado.estado()).isEqualTo("CONCILIADO"); assertThat(service.conciliar(admin,1,tipo,m.id(),new Conciliar(" Banco-001 "))).isEqualTo(conciliado);
        var rev=service.revertir(admin,1,tipo,m.id(),new Revertir(hoy,"Error de registro"));
        assertThat(rev.estado()).isEqualTo("REVERTIDO"); assertThat(rev.referencia()).isEqualTo("Banco-001"); assertThat(rev.aplicaciones()).allSatisfy(a->assertThat(a.reversionEconomicaId()).isNotNull());
        assertThat(service.revertir(admin,1,tipo,m.id(),new Revertir(hoy,"Error de registro"))).isEqualTo(rev);
        assertThat(service.registrar(admin,1,tipo,request)).isEqualTo(rev);
        mvc.perform(get(economico+"/saldo").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.saldoAritmetico").value("100.00"));
        assertThat(service.plan(admin,1,tipo,d.id())).allSatisfy(v->assertThat(v.aplicado()).isEqualTo("0.00"));
        assertThat(jdbc.queryForList("SELECT accion FROM API_Tesoreria_Auditoria WHERE movimiento=? ORDER BY id",String.class,m.id())).containsExactly("REGISTRAR","CONCILIAR","REVERTIR");
        error(409,()->service.conciliar(admin,1,tipo,m.id(),new Conciliar("Otra")));
        error(409,()->service.revertir(admin,1,tipo,m.id(),new Revertir(hoy,"Otro motivo")));
    }
    @ParameterizedTest @EnumSource(Tipo.class)
    void repartoEntreFacturasDelMismoTerceroYReintentoOrdenIndependiente(Tipo tipo) {
        var a=factura(tipo); var b=factura(tipo);
        var x=service.planificar(admin,1,tipo,a.id(),plan("100")).getFirst(); var y=service.planificar(admin,1,tipo,b.id(),plan("100")).getFirst();
        var req=entrada("125",new Reparto(x.id(),"100"),new Reparto(y.id(),"25")); var m=service.registrar(admin,1,tipo,req);
        assertThat(service.registrar(admin,1,tipo,new NuevoMovimiento(req.clave(),hoy,"125.00","TRANSFERENCIA",List.of(new Reparto(y.id(),"25.00"),new Reparto(x.id(),"100.00"))))).isEqualTo(m);
        error(409,()->service.registrar(admin,1,tipo,new NuevoMovimiento(req.clave(),hoy,"125","EFECTIVO",req.aplicaciones())));
        error(409,()->service.registrar(admin,1,tipo,entrada("76",new Reparto(y.id(),"76"))));
        assertThat(service.plan(admin,1,tipo,b.id()).getFirst().pendiente()).isEqualTo("75.00");
    }
    @ParameterizedTest @EnumSource(Tipo.class)
    void rectificativaAdmiteDevolucionSinModificarCobroOriginal(Tipo tipo) {
        var original=factura(tipo); var v=service.planificar(admin,1,tipo,original.id(),plan("100")).getFirst();
        var cobro=service.registrar(admin,1,tipo,entrada("100",new Reparto(v.id(),"100")));
        int serie=facturacion.crearSerie(admin,1,tipo,new NuevaSerie("Rect","R",hoy.getYear(),6,true)).id();
        var rect=facturacion.rectificar(admin,1,tipo,original.id(),new Rectificar(serie,hoy,tipo==Tipo.PROVEEDOR?"ABONO":null,"Rectificacion"));
        facturacion.emitir(admin,1,tipo,rect.id());
        var abono=service.planificar(admin,1,tipo,rect.id(),plan("-100")).getFirst();
        var devolucion=service.registrar(admin,1,tipo,entrada("-100",new Reparto(abono.id(),"-100")));
        assertThat(service.consultar(admin,1,tipo,cobro.id()).estado()).isEqualTo("REGISTRADO");
        assertThat(service.plan(admin,1,tipo,rect.id()).getFirst().pendiente()).isEqualTo("0.00");
        service.revertir(admin,1,tipo,devolucion.id(),new Revertir(hoy,"Devolucion errada"));
        assertThat(service.plan(admin,1,tipo,rect.id()).getFirst().pendiente()).isEqualTo("-100.00");
    }
    @Test void noSeMezclanTercerosAunqueSeanVisiblesEnLaMismaEmpresa() {
        var a=factura(Tipo.CLIENTE); var b=factura(Tipo.CLIENTE,102);
        var x=service.planificar(admin,1,Tipo.CLIENTE,a.id(),plan("100")).getFirst(); var y=service.planificar(admin,1,Tipo.CLIENTE,b.id(),plan("100")).getFirst();
        error(409,()->service.registrar(admin,1,Tipo.CLIENTE,entrada("100",new Reparto(x.id(),"50"),new Reparto(y.id(),"50"))));
        assertThat(service.plan(admin,1,Tipo.CLIENTE,a.id()).getFirst().pendiente()).isEqualTo("100.00");
    }
    @ParameterizedTest @EnumSource(Tipo.class)
    void rutasHttpDeEscrituraYDetalleFuncionan(Tipo tipo) throws Exception {
        var d=factura(tipo);
        var result=mvc.perform(put(base(tipo)+"/documentos/"+d.id()+"/vencimientos").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(plan("100")))).andExpect(status().isOk()).andReturn();
        int v=json.readTree(result.getResponse().getContentAsString()).get(0).get("id").asInt();
        var created=mvc.perform(post(base(tipo)+"/movimientos").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(entrada("100",new Reparto(v,"100"))))).andExpect(status().isCreated()).andExpect(jsonPath("$.importe").value("100.00")).andReturn();
        int m=json.readTree(created.getResponse().getContentAsString()).get("id").asInt();
        mvc.perform(get(base(tipo)+"/movimientos/"+m).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.aplicaciones[0].vencimientoId").value(v));
        mvc.perform(put(base(tipo)+"/movimientos/"+m+"/conciliacion").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(new Conciliar("HTTP-REF")))).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONCILIADO"));
        mvc.perform(put(base(tipo)+"/movimientos/"+m+"/reversion").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(new Revertir(hoy,"Error HTTP")))).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REVERTIDO"));
        mvc.perform(get(base(tipo)+"/movimientos?size=1").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].referencia").doesNotExist());
        mvc.perform(get(base(tipo)+"/vencimientos?documento="+d.id()+"&desde="+hoy.plusDays(31)).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void planRechazaSumaSignoFechasYReplanificacion() {
        var d=factura(Tipo.CLIENTE);
        error(409,()->service.planificar(admin,1,Tipo.CLIENTE,d.id(),plan("99.99")));
        error(400,()->service.planificar(admin,1,Tipo.CLIENTE,d.id(),plan("-100")));
        error(400,()->service.planificar(admin,1,Tipo.CLIENTE,d.id(),new Plan(List.of(new Cuota(hoy.minusDays(1),"100")))));
        error(400,()->service.planificar(admin,1,Tipo.CLIENTE,d.id(),new Plan(List.of(new Cuota(hoy.plusDays(2),"50"),new Cuota(hoy,"50")))));
        error(400,()->service.planificar(admin,1,Tipo.CLIENTE,d.id(),new Plan(List.of())));
        service.planificar(admin,1,Tipo.CLIENTE,d.id(),plan("100"));
        error(409,()->service.planificar(admin,1,Tipo.CLIENTE,d.id(),plan("50","50")));
    }
    @ParameterizedTest @ValueSource(strings={"0","-0.00","0.001","1e2","10000000000","NaN"," 1"})
    void decimalesInvalidosSeRechazan(String importe) {
        var d=factura(Tipo.CLIENTE); error(400,()->service.planificar(admin,1,Tipo.CLIENTE,d.id(),plan(importe)));
    }
    @Test void noSeTransformanHistoricosNiBorradores() {
        error(404,()->service.planificar(admin,1,Tipo.CLIENTE,5001,plan("100")));
        int serie=facturacion.crearSerie(admin,1,Tipo.CLIENTE,new NuevaSerie("Borrador","B",hoy.getYear(),6,false)).id();
        var d=facturacion.crear(admin,1,Tipo.CLIENTE,new Nuevo(serie,101,null,hoy,null,null,List.of(new EntradaLinea("Servicio","1","100","0","0"))));
        error(409,()->service.planificar(admin,1,Tipo.CLIENTE,d.id(),plan("100")));
    }
    @Test void movimientosExternosRequierenRevisionExplicita() {
        var d=factura(Tipo.CLIENTE);
        jdbc.update("INSERT INTO Cobros(id_factura,fecha,importe) VALUES (?,?,10)",d.facturaId(),hoy);
        error(409,()->service.planificar(admin,1,Tipo.CLIENTE,d.id(),plan("100")));
        assertThat(jdbc.queryForObject("SELECT sum(importe) FROM Cobros WHERE id_factura=?",BigDecimal.class,d.facturaId())).isEqualByComparingTo("10");
    }
    @Test void entradasDeMovimientoRechazanDuplicadosFechasRepartoYSigno() {
        var d=factura(Tipo.CLIENTE); var v=service.planificar(admin,1,Tipo.CLIENTE,d.id(),plan("100")).getFirst();
        error(400,()->service.registrar(admin,1,Tipo.CLIENTE,entrada("100",new Reparto(v.id(),"50"),new Reparto(v.id(),"50"))));
        error(409,()->service.registrar(admin,1,Tipo.CLIENTE,entrada("100",new Reparto(v.id(),"99"))));
        error(409,()->service.registrar(admin,1,Tipo.CLIENTE,entrada("-10",new Reparto(v.id(),"-10"))));
        var req=entrada("10",new Reparto(v.id(),"10"));
        for(LocalDate f:List.of(hoy.minusDays(1),hoy.plusDays(1))) error(400,()->service.registrar(admin,1,Tipo.CLIENTE,new NuevoMovimiento(req.clave(),f,req.importe(),req.metodo(),req.aplicaciones())));
        error(400,()->service.registrar(admin,1,Tipo.CLIENTE,new NuevoMovimiento("invalida",hoy,req.importe(),req.metodo(),req.aplicaciones())));
        error(400,()->service.registrar(admin,1,Tipo.CLIENTE,new NuevoMovimiento(req.clave(),hoy,req.importe(),"OTRO",req.aplicaciones())));
    }
    @Test void referenciaDeConciliacionNoSeReutilizaInclusoTrasReversion() {
        var d=factura(Tipo.CLIENTE); var v=service.planificar(admin,1,Tipo.CLIENTE,d.id(),plan("100")).getFirst();
        var a=service.registrar(admin,1,Tipo.CLIENTE,entrada("10",new Reparto(v.id(),"10"))); var b=service.registrar(admin,1,Tipo.CLIENTE,entrada("10",new Reparto(v.id(),"10")));
        service.conciliar(admin,1,Tipo.CLIENTE,a.id(),new Conciliar("REF"));
        error(409,()->service.conciliar(admin,1,Tipo.CLIENTE,b.id(),new Conciliar("REF")));
        service.revertir(admin,1,Tipo.CLIENTE,a.id(),new Revertir(hoy,"Error"));
        error(409,()->service.conciliar(admin,1,Tipo.CLIENTE,b.id(),new Conciliar("REF")));
    }
    @Test void filtrosOrdenPaginacionYCamposAdicionales() throws Exception {
        var d=factura(Tipo.CLIENTE); service.planificar(admin,1,Tipo.CLIENTE,d.id(),plan("25","75"));
        mvc.perform(get(base(Tipo.CLIENTE)+"/vencimientos?documento="+d.id()+"&size=1").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].importe").value("25.00"));
        for(String q:List.of("page=1&page=2","size=0","desde=2026-02-30","pendientes=no","otro=1","desde=2026-10-15&hasta=2026-01-01")) mvc.perform(get(base(Tipo.CLIENTE)+"/vencimientos?"+q).header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
        mvc.perform(put(base(Tipo.CLIENTE)+"/documentos/"+d.id()+"/vencimientos").header("Authorization","Bearer "+token).contentType("application/json").content("{\"cuotas\":[],\"autorId\":1}")).andExpect(status().isBadRequest());
        mvc.perform(post(base(Tipo.CLIENTE)+"/movimientos").header("Authorization","Bearer "+token).contentType("application/json").content("{\"estado\":\"CONCILIADO\"}")).andExpect(status().isBadRequest());
        mvc.perform(get(base(Tipo.CLIENTE)+"/vencimientos")).andExpect(status().isUnauthorized());
    }
    @Test void aislamientoEmpresaTipoYLecturaNoConcedeEscritura() throws Exception {
        var d=factura(Tipo.CLIENTE); var v=service.planificar(admin,1,Tipo.CLIENTE,d.id(),plan("100")).getFirst();
        var req=entrada("10",new Reparto(v.id(),"10")); var m=service.registrar(admin,1,Tipo.CLIENTE,req);
        error(404,()->service.plan(admin,2,Tipo.CLIENTE,d.id()));
        error(404,()->service.registrar(admin,2,Tipo.CLIENTE,req));
        error(404,()->service.consultar(admin,2,Tipo.CLIENTE,m.id()));
        error(404,()->service.consultar(admin,1,Tipo.PROVEEDOR,m.id()));
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",admin);
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='SOCIO'",admin);
        assertThat(service.consultar(admin,1,Tipo.CLIENTE,m.id()).id()).isEqualTo(m.id());
        error(404,()->service.registrar(admin,1,Tipo.CLIENTE,req));
        error(404,()->service.planificar(admin,1,Tipo.CLIENTE,d.id(),plan("100")));
        error(404,()->service.conciliar(admin,1,Tipo.CLIENTE,m.id(),new Conciliar("REF")));
        error(404,()->service.revertir(admin,1,Tipo.CLIENTE,m.id(),new Revertir(hoy,"Error")));
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",admin);
        jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_rol,id_empresa) SELECT ?,id_rol,1 FROM Roles WHERE nombre='TRABAJADOR'",admin);
        error(404,()->service.vencimientos(admin,1,Tipo.CLIENTE,new LinkedMultiValueMap<>()));
    }
}
