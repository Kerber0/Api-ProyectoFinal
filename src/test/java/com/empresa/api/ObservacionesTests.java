package com.empresa.api;

import com.empresa.api.laboral.EmpleoService;
import com.empresa.api.observaciones.ObservacionService;
import com.empresa.api.security.*;
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
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import static com.empresa.api.laboral.EmpleoDtos.*;
import static com.empresa.api.observaciones.ObservacionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class ObservacionesTests extends PostgreSqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired EmpleoService empleo;
    @Autowired ObservacionService service;
    @Autowired Clock clock;
    int admin,worker,asignacion;
    String token,bearer;
    LocalDate hoy;
    static final String BASE="/empresas/1/obras/1001/observaciones";
    static final String MIO="/empresas/1/mi-trabajo/obras/1001/observaciones";
    static final String CLAVE="clave-observaciones-prueba";
    @BeforeEach void preparar() {
        hoy=LocalDate.now(clock);
        jdbc.update("UPDATE Operarios SET fecha_alta=?,fecha_baja=NULL WHERE id_operario=301",hoy.minusYears(1));
        admin=usuario("test-obs-admin","ADMIN",null);
        worker=usuario("test-obs-worker","TRABAJADOR",1);
        empleo.vincular(admin,1,301,new Vincular(worker));
        asignacion=empleo.asignar(admin,1,301,new NuevaAsignacion(1001,hoy,hoy.plusDays(10))).id();
        token=login("test-obs-admin"); bearer=login("test-obs-worker");
    }
    int usuario(String nombre,String rol,Integer empresa) {
        int id=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,?) RETURNING id_usuario",Integer.class,nombre,passwords.encode(CLAVE));
        if(empresa==null) jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre=?",id,rol);
        else jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) SELECT ?,?,id_rol FROM Roles WHERE nombre=?",id,empresa,rol);
        return id;
    }
    String login(String nombre) { return auth.login(new AuthDtos.Login(nombre,CLAVE)).accessToken(); }
    ResultActions getAs(String path,String t) throws Exception { return mvc.perform(get("/api/v1"+path).header("Authorization","Bearer "+t)); }
    ResultActions postAs(String path,Object body,String t) throws Exception {
        return mvc.perform(post("/api/v1"+path).header("Authorization","Bearer "+t).contentType("application/json").content(json.writeValueAsString(body)));
    }
    ResultActions putAs(String path,Object body,String t) throws Exception {
        return mvc.perform(put("/api/v1"+path).header("Authorization","Bearer "+t).contentType("application/json").content(json.writeValueAsString(body)));
    }
    Detalle crear() { return service.crear(worker,1,1001,true,new Nueva("Revisar la puerta de acceso")); }

    @Test void trabajadorCreaConAutorYFechaDelServidorYDetalleOperativoMinimo() throws Exception {
        var before=clock.instant().minusSeconds(1);
        var response=postAs(MIO,new Nueva("  Revisar acceso\nMedida pendiente  "),bearer).andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorId").value(worker)).andExpect(jsonPath("$.estado").value("ABIERTA"))
                .andExpect(jsonPath("$.resueltoPor").isEmpty()).andExpect(jsonPath("$.fechaResolucion").isEmpty())
                .andExpect(jsonPath("$.resolucion").isEmpty()).andReturn().getResponse().getContentAsString();
        var o=json.readValue(response,Detalle.class);
        assertThat(o.fechaCreacion()).isBetween(before,clock.instant());
        assertThat(o.texto()).isEqualTo("Revisar acceso\nMedida pendiente");
        assertThat(json.readTree(response).properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "id","obraId","autorId","fechaCreacion","estado","texto","resueltoPor","fechaResolucion","resolucion");
        getAs(BASE+"/"+o.id(),token).andExpect(status().isOk()).andExpect(content().json(response));
        assertThat(jdbc.queryForObject("SELECT autor FROM API_Observaciones_Auditoria WHERE observacion=? AND accion='CREAR'",Integer.class,o.id())).isEqualTo(worker);
    }
    @Test void resolucionIdempotenteConservaTextoAutorFechaYAuditaUnaVez() throws Exception {
        var original=crear();
        String path=BASE+"/"+original.id()+"/resolucion";
        var response=putAs(path,new Resolver("Acceso revisado"),token).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESUELTA")).andExpect(jsonPath("$.resueltoPor").value(admin))
                .andExpect(jsonPath("$.autorId").value(worker)).andExpect(jsonPath("$.texto").value(original.texto()))
                .andReturn().getResponse().getContentAsString();
        putAs(path,new Resolver(" Acceso revisado "),token).andExpect(status().isOk()).andExpect(content().json(response));
        putAs(path,new Resolver("Otra explicacion"),token).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CONFLICTO"));
        getAs(MIO+"/"+original.id(),bearer).andExpect(status().isOk()).andExpect(content().json(response));
        assertThat(jdbc.queryForList("SELECT accion FROM API_Observaciones_Auditoria WHERE observacion=? ORDER BY id",String.class,original.id())).containsExactly("CREAR","RESOLVER");
        assertThat(json.readValue(response,Detalle.class).fechaCreacion()).isEqualTo(original.fechaCreacion());
    }
    @Test void listadoFiltraEstadoPaginaYNoIncluyeTextos() throws Exception {
        var first=crear(); var second=crear();
        service.resolver(admin,1,1001,first.id(),new Resolver("Comprobado"));
        getAs(MIO+"?size=1&page=1",bearer).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(second.id())).andExpect(jsonPath("$.items[0].texto").doesNotExist())
                .andExpect(jsonPath("$.items[0].resolucion").doesNotExist());
        getAs(BASE+"?estado=RESUELTA",token).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(first.id()));
        getAs(MIO+"?estado=ABIERTA",bearer).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].id").value(second.id()));
        getAs(MIO+"?page=100000",bearer).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
    }
    @Test void trabajadorNoEscribeNiResuelveMedianteRutasGenerales() throws Exception {
        var o=crear();
        getAs(BASE,bearer).andExpect(status().isNotFound());
        postAs(BASE,new Nueva("No autorizada"),bearer).andExpect(status().isNotFound());
        putAs(BASE+"/"+o.id()+"/resolucion",new Resolver("No autorizada"),bearer).andExpect(status().isNotFound());
        putAs(MIO+"/"+o.id()+"/resolucion",new Resolver("No autorizada"),bearer).andExpect(status().isForbidden());
        for(String path:new String[]{"/empresas/1/obras/1001","/empresas/1/clientes","/empresas/1/facturas-cliente"})
            getAs(path,bearer).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"SOCIO","LECTURA_EMPRESA"})
    void lectoresConsultanSinCrearNiResolver(String rol) throws Exception {
        usuario("test-obs-lector",rol,rol.equals("SOCIO")?null:1);
        String lector=login("test-obs-lector"); var o=crear();
        getAs(BASE,lector).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        getAs(BASE+"/"+o.id(),lector).andExpect(status().isOk());
        postAs(BASE,new Nueva("No autorizada"),lector).andExpect(status().isNotFound());
        putAs(BASE+"/"+o.id()+"/resolucion",new Resolver("No autorizada"),lector).andExpect(status().isNotFound());
        postAs(MIO,new Nueva("No autorizada"),lector).andExpect(status().isNotFound());
    }
    @Test void identidadYEstadoNoSeAceptanDesdeCliente() throws Exception {
        postAs(MIO,Map.of("texto","Prueba","autorId",admin,"estado","RESUELTA","obraId",1003),bearer)
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Observaciones_Obra",Integer.class)).isZero();
        var o=crear();
        putAs(BASE+"/"+o.id()+"/resolucion",Map.of("resolucion","Prueba","resueltoPor",worker),token).andExpect(status().isBadRequest());
        getAs(BASE+"/"+o.id(),token).andExpect(jsonPath("$.estado").value("ABIERTA"));
    }
    @ParameterizedTest @ValueSource(strings={"asignacion","baja","alta-futura","entidad","empresa","permiso","rol","cliente","vinculo"})
    void revocacionesRetiranLecturaYCreacionPropias(String caso) throws Exception {
        var o=crear();
        switch(caso) {
            case "asignacion" -> empleo.cancelarAsignacion(admin,1,301,asignacion);
            case "baja" -> jdbc.update("UPDATE Operarios SET fecha_baja=? WHERE id_operario=301",hoy.minusDays(1));
            case "alta-futura" -> jdbc.update("UPDATE Operarios SET fecha_alta=? WHERE id_operario=301",hoy.plusDays(1));
            case "entidad" -> jdbc.update("UPDATE Entidades SET activo=false WHERE id_entidad=301");
            case "empresa" -> jdbc.update("UPDATE API_Empresas SET activo=false WHERE id_empresa=1");
            case "permiso" -> jdbc.update("UPDATE Permisos SET activo=false WHERE nombre='OBSERVACIONES'");
            case "rol" -> jdbc.update("UPDATE Roles SET activo=false WHERE nombre='TRABAJADOR'");
            case "cliente" -> jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_cliente=101 AND id_empresa=1");
            case "vinculo" -> { empleo.desvincular(admin,1,301); bearer=login("test-obs-worker"); }
        }
        getAs(MIO,bearer).andExpect(status().isNotFound());
        getAs(MIO+"/"+o.id(),bearer).andExpect(status().isNotFound());
        postAs(MIO,new Nueva("Fuera de ambito"),bearer).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Observaciones_Obra",Integer.class)).isEqualTo(1);
    }
    @Test void otraObraEmpresaYObservacionNoSeMezclan() throws Exception {
        var o=crear();
        for(String path:new String[]{"/empresas/1/obras/1002/observaciones/","/empresas/2/obras/1003/observaciones/","/empresas/2/obras/1001/observaciones/"}) {
            getAs(path+o.id(),token).andExpect(status().isNotFound());
            putAs(path+o.id()+"/resolucion",new Resolver("No autorizada"),token).andExpect(status().isNotFound());
        }
        getAs("/empresas/1/obras/1002/observaciones",token).andExpect(jsonPath("$.totalElements").value(0));
        postAs("/empresas/1/mi-trabajo/obras/1002/observaciones",new Nueva("Sin asignacion"),bearer).andExpect(status().isNotFound());
        postAs("/empresas/1/obras/1003/observaciones",new Nueva("Otra empresa"),token).andExpect(status().isNotFound());
        getAs(BASE+"/2147483647",token).andExpect(status().isNotFound());
    }
    @Test void adminSinEmpleadoPuedeGestionarPeroNoUsarConsultaPropia() throws Exception {
        postAs(BASE,new Nueva("Observacion de administracion"),token).andExpect(status().isCreated()).andExpect(jsonPath("$.autorId").value(admin));
        getAs(MIO,token).andExpect(status().isNotFound());
        postAs(MIO,new Nueva("Sin empleado"),token).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={""," ","\t\n","\u0000","texto\u001b"})
    void rechazaTextoVacioYControlesSinEscribir(String texto) throws Exception {
        var o=crear();
        postAs(MIO,new Nueva(texto),bearer).andExpect(status().isBadRequest());
        putAs(BASE+"/"+o.id()+"/resolucion",new Resolver(texto),token).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Observaciones_Auditoria",Integer.class)).isEqualTo(1);
    }
    @Test void validaLimiteUnicodeYJsonIncompleto() throws Exception {
        postAs(MIO,new Nueva("a".repeat(2001)),bearer).andExpect(status().isBadRequest());
        postAs(MIO,new Nueva("\uD83D\uDD27".repeat(2000)),bearer).andExpect(status().isCreated());
        postAs(MIO,Map.of(),bearer).andExpect(status().isBadRequest());
        var o=crear();
        putAs(BASE+"/"+o.id()+"/resolucion",new Resolver("a".repeat(2001)),token).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={"?estado=OTRA","?estado=","?estado=ABIERTA&estado=RESUELTA","?size=0","?size=101","?page=-1","?page=100001","?page=x","?size=1&size=2","?autorId=1","?desde=2026-10-15"})
    void parametrosInvalidosNoAmplianAmbito(String query) throws Exception {
        getAs(MIO+query,bearer).andExpect(status().isBadRequest());
        getAs(BASE+query,token).andExpect(status().isBadRequest());
    }
    @Test void noEditaNiBorraNiConfundeIncidenciasYSinSesionRechaza() throws Exception {
        int incidencias=jdbc.queryForObject("SELECT count(*) FROM Incidencias",Integer.class); var o=crear();
        mvc.perform(delete("/api/v1"+BASE+"/"+o.id()).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        putAs(BASE+"/"+o.id(),new Nueva("Cambiar original"),token).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1"+MIO)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1"+MIO).contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Incidencias",Integer.class)).isEqualTo(incidencias);
    }
    @Test void baseImpideRelacionConEmpresaAjena() {
        assertThatThrownBy(()->jdbc.update("INSERT INTO API_Observaciones_Obra(id_empresa,id_obra,autor,fecha_creacion,texto) VALUES (2,1001,?,CURRENT_TIMESTAMP,'Prueba')",worker))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void baseImpideEstadoResueltoSinAutorFechaYTexto() {
        var o=crear();
        assertThatThrownBy(()->jdbc.update("UPDATE API_Observaciones_Obra SET estado='RESUELTA' WHERE id=?",o.id()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
