package com.empresa.api;

import com.empresa.api.laboral.EmpleoService;
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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class EmpleoTests extends PostgreSqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired EmpleoService service;
    @Autowired Clock clock;
    int admin,worker;
    String token,bearer;
    LocalDate hoy;
    static final String BASE="/empresas/1/operarios/301";
    static final String MIO="/empresas/1/mi-trabajo";
    static final String CLAVE="clave-empleo-ficticia";
    @BeforeEach void preparar() {
        hoy=LocalDate.now(clock);
        // Fechas relativas al reloj; no se depende del ano de los fixtures.
        jdbc.update("UPDATE Operarios SET fecha_alta=?,fecha_baja=NULL WHERE id_operario IN (301,302)",hoy.minusYears(2));
        admin=crearUsuario("test-empleo-admin","ADMIN",null);
        worker=crearUsuario("test-empleo-worker","TRABAJADOR",1);
        token=login("test-empleo-admin");
        service.vincular(admin,1,301,new Vincular(worker));
        bearer=login("test-empleo-worker");
    }
    int crearUsuario(String nombre,String rol,Integer empresa) {
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
    ResultActions deleteAs(String path,String t) throws Exception { return mvc.perform(delete("/api/v1"+path).header("Authorization","Bearer "+t)); }
    String periodo() { return "?desde="+hoy.minusDays(5)+"&hasta="+hoy.plusDays(5); }
    Asignacion asignar(int obra,LocalDate desde,LocalDate hasta) { return service.asignar(admin,1,301,new NuevaAsignacion(obra,desde,hasta)); }
    long auditorias() { return jdbc.queryForObject("SELECT count(*) FROM API_Laboral_Auditoria WHERE autor=?",Long.class,admin); }

    @Test void vinculoExplicitoNoDaPermisosGeneralesNiExponeDatosPersonales() throws Exception {
        getAs(MIO+"/empleado",bearer).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(301));
        var body=getAs(MIO+"/empleado",bearer).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder("id","codigo","nombre");
        for(String path:new String[]{"/empresas/1/obras","/empresas/1/obras/1001","/empresas/1/clientes","/empresas/1/partes-mano-obra","/empresas/1/facturas-cliente"})
            getAs(path,bearer).andExpect(status().isNotFound());
    }
    @Test void noDeduceVinculoDesdeEntidadFiscal() throws Exception {
        service.desvincular(admin,1,301);
        jdbc.update("UPDATE Usuarios SET id_entidad=301 WHERE id_usuario=?",worker);
        bearer=login("test-empleo-worker");
        getAs(MIO+"/empleado",bearer).andExpect(status().isNotFound());
    }
    @Test void vincularEsIdempotenteYDesvincularRevocaSesiones() throws Exception {
        long count=auditorias();
        putAs(BASE+"/usuario",new Vincular(worker),token).andExpect(status().isNoContent());
        assertThat(auditorias()).isEqualTo(count);
        getAs(MIO+"/empleado",bearer).andExpect(status().isOk());
        deleteAs(BASE+"/usuario",token).andExpect(status().isNoContent());
        getAs(MIO+"/empleado",bearer).andExpect(status().isUnauthorized());
        deleteAs(BASE+"/usuario",token).andExpect(status().isNoContent());
        assertThat(auditorias()).isEqualTo(count+1);
        getAs(BASE+"/usuario",token).andExpect(status().isNotFound());
    }
    @Test void cuentaYEmpleadoSonUnicosYSoloAdminGlobalGestionaVinculos() throws Exception {
        int otro=crearUsuario("test-empleo-otro","TRABAJADOR",1);
        putAs(BASE+"/usuario",new Vincular(otro),token).andExpect(status().isConflict());
        putAs("/empresas/2/operarios/302/usuario",new Vincular(worker),token).andExpect(status().isConflict());
        getAs(BASE+"/usuario",bearer).andExpect(status().isNotFound());
        crearUsuario("test-empleo-local","ADMIN",1);
        putAs(BASE+"/usuario",new Vincular(worker),login("test-empleo-local")).andExpect(status().isNotFound());
    }
    @Test void sinAsignacionNoHayObrasAunqueExistanPartesHistoricos() throws Exception {
        getAs(MIO+"/obras",bearer).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        getAs(MIO+"/obras/1001",bearer).andExpect(status().isNotFound());
        getAs(MIO+"/obras/1001/contactos",bearer).andExpect(status().isNotFound());
    }
    @Test void obraAsignadaIncluyeUbicacionYContactosMinimosSinImportesNiTextosLibres() throws Exception {
        asignar(1001,hoy,hoy);
        getAs(MIO+"/obras",bearer).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        var body=getAs(MIO+"/obras/1001",bearer).andExpect(status().isOk()).andExpect(jsonPath("$.ubicacion.direccion").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body).properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("id","codigo","titulo","estado","fechaInicio","fechaFinPrevista","ubicacion");
        var contacts=getAs(MIO+"/obras/1001/contactos",bearer).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(contacts).at("/items/0").properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("id","nombre","apellidos","telefono","movil");
        getAs(MIO+"/obras/1002",bearer).andExpect(status().isNotFound());
    }
    @Test void asignacionesFuturasPasadasYCanceladasNoAutorizan() throws Exception {
        asignar(1001,hoy.minusDays(2),hoy.minusDays(1));
        asignar(1001,hoy.plusDays(1),null);
        var actual=asignar(1001,hoy,hoy);
        deleteAs(BASE+"/asignaciones/"+actual.id(),token).andExpect(status().isNoContent());
        deleteAs(BASE+"/asignaciones/"+actual.id(),token).andExpect(status().isNoContent());
        getAs(MIO+"/obras",bearer).andExpect(jsonPath("$.totalElements").value(0));
        getAs(BASE+"/asignaciones"+periodo(),token).andExpect(jsonPath("$.totalElements").value(3));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Laboral_Auditoria WHERE accion='CANCELAR_ASIGNACION' AND referencia=?",Integer.class,actual.id())).isEqualTo(1);
    }
    @Test void aislamientoYCoherenciaDeObraTambienEnConteosYContactos() throws Exception {
        postAs(BASE+"/asignaciones",new NuevaAsignacion(1003,hoy,null),token).andExpect(status().isNotFound());
        postAs("/empresas/1/operarios/302/asignaciones",new NuevaAsignacion(1001,hoy,null),token).andExpect(status().isNotFound());
        asignar(1001,hoy,null);
        jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_cliente=101 AND id_empresa=1");
        getAs(MIO+"/obras",bearer).andExpect(jsonPath("$.totalElements").value(0));
        getAs(MIO+"/obras/1001/contactos",bearer).andExpect(status().isNotFound());
        getAs("/empresas/2/mi-trabajo/empleado",bearer).andExpect(status().isNotFound());
    }
    @Test void contactosAjenosOInactivosSeExcluyen() throws Exception {
        asignar(1001,hoy,null);
        jdbc.update("UPDATE Contactos SET id_entidad=102 WHERE id_contacto=101");
        getAs(MIO+"/obras/1001/contactos",bearer).andExpect(jsonPath("$.totalElements").value(0));
        jdbc.update("UPDATE Contactos SET id_entidad=101,activo=false WHERE id_contacto=101");
        getAs(MIO+"/obras/1001/contactos",bearer).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void obraSinUbicacionConservaNulo() throws Exception {
        service.desvincular(admin,1,301); service.vincular(admin,2,302,new Vincular(worker));
        jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) SELECT ?,2,id_rol FROM Roles WHERE nombre='TRABAJADOR'",worker);
        service.asignar(admin,2,302,new NuevaAsignacion(1003,hoy,null));
        getAs("/empresas/2/mi-trabajo/obras/1003",login("test-empleo-worker"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ubicacion").isEmpty()).andExpect(jsonPath("$.fechaInicio").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"baja","alta-futura","entidad","empresa","rol","permiso","asignacion-rol"})
    void consultaPropiaRevalidaVigencia(String caso) throws Exception {
        asignar(1001,hoy,null);
        switch(caso) {
            case "baja" -> jdbc.update("UPDATE Operarios SET fecha_baja=? WHERE id_operario=301",hoy.minusDays(1));
            case "alta-futura" -> jdbc.update("UPDATE Operarios SET fecha_alta=? WHERE id_operario=301",hoy.plusDays(1));
            case "entidad" -> jdbc.update("UPDATE Entidades SET activo=false WHERE id_entidad=301");
            case "empresa" -> jdbc.update("UPDATE API_Empresas SET activo=false WHERE id_empresa=1");
            case "rol" -> jdbc.update("UPDATE Roles SET activo=false WHERE nombre='TRABAJADOR'");
            case "permiso" -> jdbc.update("UPDATE Permisos SET activo=false WHERE nombre='TRABAJO_PROPIO'");
            case "asignacion-rol" -> jdbc.update("DELETE FROM API_Accesos_Empresa WHERE id_usuario=?",worker);
        }
        getAs(MIO+"/obras",bearer).andExpect(status().isNotFound());
    }
    @Test void rechazaSolapesInclusivosPeroPermiteObrasDistintas() throws Exception {
        asignar(1001,hoy,null);
        postAs(BASE+"/asignaciones",new NuevaAsignacion(1001,hoy.plusDays(1),null),token).andExpect(status().isConflict());
        postAs(BASE+"/asignaciones",new NuevaAsignacion(1001,hoy.minusDays(1),hoy),token).andExpect(status().isConflict());
        postAs(BASE+"/asignaciones",new NuevaAsignacion(1002,hoy,null),token).andExpect(status().isCreated());
        getAs(MIO+"/obras?size=1&page=1",bearer).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].id").value(1002));
    }
    @Test void ausenciaPropiaSinDatosMedicosYCancelacionConservaHistoria() throws Exception {
        var response=postAs(BASE+"/ausencias",new NuevaAusencia(hoy,hoy.plusDays(2),TipoAusencia.PERMISO),token)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id=json.readTree(response).get("id").asInt();
        getAs(MIO+"/ausencias"+periodo(),bearer).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id));
        assertThat(json.readTree(response).properties()).extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("id","operarioId","desde","hasta","tipo","cancelada","justificada","observaciones");
        postAs(BASE+"/ausencias",new NuevaAusencia(hoy.plusDays(2),hoy.plusDays(3),TipoAusencia.OTRA),token).andExpect(status().isConflict());
        deleteAs(BASE+"/ausencias/"+id,token).andExpect(status().isNoContent());
        deleteAs(BASE+"/ausencias/"+id,token).andExpect(status().isNoContent());
        getAs(MIO+"/ausencias"+periodo(),bearer).andExpect(jsonPath("$.items[0].cancelada").value(true));
        postAs(BASE+"/ausencias",new NuevaAusencia(hoy,hoy,TipoAusencia.VACACIONES),token).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Laboral_Auditoria WHERE accion='CANCELAR_AUSENCIA' AND referencia=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void ausenciaConFaltaJustificadaYObservacionesSoloParaAdministracion() throws Exception {
        var response=postAs(BASE+"/ausencias",new NuevaAusencia(hoy,hoy,TipoAusencia.FALTA,true,"Autorizada por administración"),token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("FALTA"))
                .andExpect(jsonPath("$.justificada").value(true))
                .andExpect(jsonPath("$.observaciones").value("Autorizada por administración"))
                .andReturn().getResponse().getContentAsString();
        int id=json.readTree(response).get("id").asInt();
        getAs(BASE+"/ausencias"+periodo(),token)
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].justificada").value(true));
        getAs(MIO+"/ausencias"+periodo(),bearer)
                .andExpect(jsonPath("$.items[0].tipo").value("FALTA"))
                .andExpect(jsonPath("$.items[0].justificada").doesNotExist())
                .andExpect(jsonPath("$.items[0].observaciones").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT observaciones FROM API_Ausencias WHERE id=?",String.class,id))
                .isEqualTo("Autorizada por administración");
    }
    @Test void ausenciaRechazaObservacionDemasiadoLarga() throws Exception {
        postAs(BASE+"/ausencias",new NuevaAusencia(hoy,hoy,TipoAusencia.FALTA,false,"x".repeat(1001)),token)
                .andExpect(status().isBadRequest());
    }
    @Test void ausenciaNoModificaAsignacionesNiPartesHistoricos() throws Exception {
        long partes=jdbc.queryForObject("SELECT count(*) FROM Partes_Mano_Obra",Long.class);
        asignar(1001,hoy,null);
        service.ausencia(admin,1,301,new NuevaAusencia(hoy,hoy,TipoAusencia.BAJA));
        getAs(MIO+"/obras/1001",bearer).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Partes_Mano_Obra",Long.class)).isEqualTo(partes);
    }
    @ParameterizedTest @ValueSource(strings={"SOCIO","LECTURA_EMPRESA","TRABAJADOR"})
    void lectoresNoGestionanYNoVenAusenciasAjenas(String rol) throws Exception {
        crearUsuario("test-empleo-lector",rol,rol.equals("SOCIO")?null:1);
        String t=login("test-empleo-lector");
        getAs(BASE+"/ausencias"+periodo(),t).andExpect(status().isNotFound());
        postAs(BASE+"/ausencias",new NuevaAusencia(hoy,hoy,TipoAusencia.BAJA),t).andExpect(status().isNotFound());
        postAs(BASE+"/asignaciones",new NuevaAsignacion(1001,hoy,null),t).andExpect(status().isNotFound());
        getAs(BASE+"/asignaciones"+periodo(),t).andExpect(status().is(rol.equals("TRABAJADOR")?404:200));
        getAs(MIO+"/ausencias"+periodo(),t).andExpect(status().isNotFound());
    }
    @Test void noCancelaRegistrosDeOtroEmpleadoOEmpresa() throws Exception {
        var a=service.ausencia(admin,2,302,new NuevaAusencia(hoy,hoy,TipoAusencia.PERMISO));
        var b=service.asignar(admin,2,302,new NuevaAsignacion(1003,hoy,null));
        deleteAs(BASE+"/ausencias/"+a.id(),token).andExpect(status().isNotFound());
        deleteAs(BASE+"/asignaciones/"+b.id(),token).andExpect(status().isNotFound());
        getAs(MIO+"/ausencias"+periodo(),bearer).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void validaFechasTiposYPeriodoLaboralAntesDePersistir() throws Exception {
        long antes=auditorias();
        postAs(BASE+"/asignaciones",new NuevaAsignacion(1001,hoy,hoy.minusDays(1)),token).andExpect(status().isBadRequest());
        postAs(BASE+"/ausencias",Map.of("desde",hoy.toString(),"hasta",hoy.toString(),"tipo","DIAGNOSTICO"),token).andExpect(status().isBadRequest());
        jdbc.update("UPDATE Operarios SET fecha_baja=? WHERE id_operario=301",hoy.plusDays(1));
        postAs(BASE+"/asignaciones",new NuevaAsignacion(1001,hoy,null),token).andExpect(status().isConflict());
        postAs(BASE+"/ausencias",new NuevaAusencia(hoy,hoy.plusDays(2),TipoAusencia.BAJA),token).andExpect(status().isConflict());
        assertThat(auditorias()).isEqualTo(antes);
    }
    @ParameterizedTest @ValueSource(strings={"","?desde=2026-01-01","?desde=2026-02-30&hasta=2026-03-01","?desde=2026-01-02&hasta=2026-01-01","?desde=2026-01-01&hasta=2028-01-01","?desde=2026-01-01&hasta=2026-02-01&page=-1","?desde=2026-01-01&hasta=2026-02-01&size=101","?desde=2026-01-01&hasta=2026-02-01&size=1&size=2","?desde=2026-01-01&hasta=2026-02-01&operario=302"})
    void filtrosInvalidosNoPermitenAmpliarAmbito(String query) throws Exception {
        getAs(MIO+"/ausencias"+query,bearer).andExpect(status().isBadRequest());
        getAs(BASE+"/asignaciones"+query,token).andExpect(status().isBadRequest());
    }
    @Test void rutasExigenSesionYTrabajadorNoPuedeElegirFechaDeAcceso() throws Exception {
        mvc.perform(get("/api/v1"+MIO+"/obras")).andExpect(status().isUnauthorized());
        getAs(MIO+"/obras?desde="+hoy,bearer).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1"+BASE+"/ausencias").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
}