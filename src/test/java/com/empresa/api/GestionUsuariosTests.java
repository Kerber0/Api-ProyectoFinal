package com.empresa.api;

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
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class GestionUsuariosTests extends PostgreSqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired GestionUsuariosService service;
    int admin;
    String token;
    static final String CLAVE="clave-ficticia-12345";
    @BeforeEach void preparar() {
        admin=usuario("test-gestion-admin");
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) VALUES (?,?)",admin,rol("ADMIN"));
        token=login("test-gestion-admin").accessToken();
    }
    int rol(String nombre) { return jdbc.queryForObject("SELECT id_rol FROM Roles WHERE nombre=?",Integer.class,nombre); }
    int usuario(String nombre) {
        return jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,?) RETURNING id_usuario",
                Integer.class,nombre,passwords.encode(CLAVE));
    }
    AuthDtos.Tokens login(String nombre) { return auth.login(new AuthDtos.Login(nombre,CLAVE)); }
    ResultActions getAs(String path,String bearer) throws Exception {
        return mvc.perform(get("/api/v1"+path).header("Authorization","Bearer "+bearer));
    }
    ResultActions putAs(String path,Object body,String bearer) throws Exception {
        var request=put("/api/v1"+path).header("Authorization","Bearer "+bearer);
        if(body!=null) request.contentType("application/json").content(json.writeValueAsString(body));
        return mvc.perform(request);
    }
    ResultActions deleteAs(String path,String bearer) throws Exception {
        return mvc.perform(delete("/api/v1"+path).header("Authorization","Bearer "+bearer));
    }
    ResultActions crear(String nombre,String clave) throws Exception {
        return mvc.perform(post("/api/v1/usuarios").header("Authorization","Bearer "+token)
                .contentType("application/json").content(json.writeValueAsString(Map.of("usuario",nombre,"password",clave))));
    }
    long auditorias(int id) { return jdbc.queryForObject("SELECT count(*) FROM API_Usuarios_Auditoria WHERE usuario=?",Long.class,id); }

    @Test void crearCuentaSinAccesosNiDatosPersonalesYConHash() throws Exception {
        var response=crear("test-nueva",CLAVE).andExpect(status().isCreated())
                .andExpect(header().exists("Location")).andExpect(jsonPath("$.usuario").value("test-nueva"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.emailLogin").doesNotExist()).andExpect(jsonPath("$.entidad").doesNotExist())
                .andReturn().getResponse();
        int id=json.readTree(response.getContentAsString()).get("id").asInt();
        assertThat(response.getHeader("Location")).isEqualTo("/api/v1/usuarios/"+id);
        String hash=jdbc.queryForObject("SELECT password_hash FROM Usuarios WHERE id_usuario=?",String.class,id);
        assertThat(hash).isNotEqualTo(CLAVE); assertThat(passwords.matches(CLAVE,hash)).isTrue();
        assertThat(auditorias(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT autor FROM API_Usuarios_Auditoria WHERE usuario=?",Integer.class,id)).isEqualTo(admin);
        getAs("/empresas",login("test-nueva").accessToken()).andExpect(content().json("[]"));
        getAs("/usuarios/"+id+"/accesos",token).andExpect(content().json("[]"));
    }
    @Test void duplicadoRespetaCollationYDevuelveConflictoSinSql() throws Exception {
        crear("test-gestión",CLAVE).andExpect(status().isCreated());
        crear("TEST-GESTION",CLAVE).andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }
    @ParameterizedTest @ValueSource(strings={"corta"," ","ááááááááááááááááááááááááááááááááááááá","😀😀😀😀😀😀"})
    void rechazaClavesInvalidasIncluidoLimiteUtf8(String clave) throws Exception {
        crear("test-invalid",clave).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Usuarios WHERE nombre_usuario='test-invalid'",Integer.class)).isZero();
    }
    @Test void noSerializaNiImprimePasswordsDeEntrada() throws Exception {
        var create=new GestionUsuariosDtos.Crear("cuenta",CLAVE);
        var password=new GestionUsuariosDtos.Password(CLAVE);
        assertThat(json.writeValueAsString(create)).doesNotContain(CLAVE);
        assertThat(json.writeValueAsString(password)).doesNotContain(CLAVE);
        assertThat(create.toString()).doesNotContain(CLAVE);
        assertThat(password.toString()).doesNotContain(CLAVE);
    }
    @Test void socioLeeCuentasMinimasPeroNoAdministra() throws Exception {
        int socio=usuario("test-socio-gestion");
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) VALUES (?,?)",socio,rol("SOCIO"));
        String socioToken=login("test-socio-gestion").accessToken();
        getAs("/usuarios?size=1",socioToken).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        getAs("/roles",socioToken).andExpect(status().isOk());
        getAs("/permisos",socioToken).andExpect(status().isOk());
        putAs("/usuarios/"+admin,Map.of("usuario","otro","activo",true),socioToken).andExpect(status().isNotFound());
        putAs("/usuarios/"+socio+"/roles-globales/"+rol("ADMIN"),null,socioToken).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","LECTURA_EMPRESA","TRABAJADOR"})
    void rolSoloLocalNoPermiteGestionGlobal(String nombreRol) throws Exception {
        int local=usuario("test-local-gestion");
        jdbc.update("INSERT INTO API_Accesos_Empresa VALUES (?,?,?)",local,1,rol(nombreRol));
        String localToken=login("test-local-gestion").accessToken();
        for(String path:new String[]{"/usuarios","/usuarios/"+admin,"/usuarios/"+admin+"/accesos","/roles","/permisos"})
            getAs(path,localToken).andExpect(status().isNotFound());
        putAs("/usuarios/"+local+"/roles-globales/"+rol("ADMIN"),null,localToken).andExpect(status().isNotFound());
        putAs("/usuarios/"+admin+"/password",Map.of("password",CLAVE),localToken).andExpect(status().isNotFound());
        deleteAs("/usuarios/"+admin+"/roles-globales/"+rol("ADMIN"),localToken).andExpect(status().isNotFound());
        getAs("/empresas/2/permisos",localToken).andExpect(status().isNotFound());
    }
    @Test void asignarRevocarLocalEsIdempotenteYRevocaSesiones() throws Exception {
        int id=usuario("test-asignacion"); var sesion=login("test-asignacion");
        String path="/usuarios/"+id+"/empresas/1/roles/"+rol("LECTURA_EMPRESA");
        putAs(path,null,token).andExpect(status().isNoContent());
        putAs(path,null,token).andExpect(status().isNoContent());
        assertThat(auditorias(id)).isEqualTo(1);
        getAs("/auth/me",sesion.accessToken()).andExpect(status().isUnauthorized());
        var nueva=login("test-asignacion");
        getAs("/empresas/1/obras",nueva.accessToken()).andExpect(status().isOk());
        getAs("/empresas/2/obras",nueva.accessToken()).andExpect(status().isNotFound());
        putAs(path,null,token).andExpect(status().isNoContent());
        getAs("/auth/me",nueva.accessToken()).andExpect(status().isOk());
        deleteAs(path,token).andExpect(status().isNoContent());
        deleteAs(path,token).andExpect(status().isNoContent());
        assertThat(auditorias(id)).isEqualTo(2);
        getAs("/auth/me",nueva.accessToken()).andExpect(status().isUnauthorized());
        getAs("/empresas",login("test-asignacion").accessToken()).andExpect(content().json("[]"));
    }
    @Test void globalSoloRolesRegistradosYNoDependeDelNombreDeUsuario() throws Exception {
        int id=usuario("test-global");
        putAs("/usuarios/"+id+"/roles-globales/"+rol("TRABAJADOR"),null,token).andExpect(status().isNotFound());
        String path="/usuarios/"+id+"/roles-globales/"+rol("SOCIO");
        putAs(path,null,token).andExpect(status().isNoContent());
        getAs("/empresas",login("test-global").accessToken()).andExpect(jsonPath("$.length()").value(4));
        getAs("/usuarios/"+id+"/accesos",token).andExpect(jsonPath("$[0].global").value(true));
        deleteAs(path,token).andExpect(status().isNoContent());
        assertThat(auditorias(id)).isEqualTo(2);
    }
    @Test void trabajadorSoloVeEmpresaSinEconomiaClientesNiObrasGenerales() throws Exception {
        int id=usuario("test-trabajador");
        service.asignar(admin,id,1,rol("TRABAJADOR"),true);
        String worker=login("test-trabajador").accessToken();
        getAs("/empresas/1/permisos",worker).andExpect(content().json("[{\"permiso\":\"EMPRESA\",\"nivel\":\"LECTURA\"},{\"permiso\":\"OBSERVACIONES\",\"nivel\":\"ESCRITURA\"},{\"permiso\":\"TRABAJO_PROPIO\",\"nivel\":\"LECTURA\"}]"));
        for(String path:new String[]{"/obras","/obras/1001","/obras/1001/contactos","/clientes","/facturas-cliente","/operarios","/tareas","/incidencias"})
            getAs("/empresas/1"+path,worker).andExpect(status().isNotFound());
    }
    @Test void permisosEfectivosTomanMayorNivelYReflejanRevocacion() throws Exception {
        int id=usuario("test-efectivos");
        service.asignar(admin,id,1,rol("LECTURA_EMPRESA"),true);
        service.asignar(admin,id,1,rol("ADMIN"),true);
        String bearer=login("test-efectivos").accessToken();
        getAs("/empresas/1/permisos",bearer).andExpect(jsonPath("$[?(@.permiso == 'USUARIOS')].nivel").value("ADMIN"));
        jdbc.update("UPDATE Permisos SET activo=false WHERE nombre='USUARIOS'");
        getAs("/empresas/1/permisos",bearer).andExpect(jsonPath("$[?(@.permiso == 'USUARIOS')]").isEmpty());
        getAs("/usuarios",token).andExpect(status().isNotFound());
    }
    @Test void cambiarPasswordRevocaAccessYRefreshYNoGuardaSecretosEnAuditoria() throws Exception {
        int id=usuario("test-password"); var sesion=login("test-password");
        putAs("/usuarios/"+id+"/password",Map.of("password","clave-nueva-12345"),token).andExpect(status().isNoContent());
        getAs("/auth/me",sesion.accessToken()).andExpect(status().isUnauthorized());
        assertThatThrownBy(()->auth.refresh(sesion.refreshToken())).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        assertThatThrownBy(()->login("test-password")).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        assertThat(auth.login(new AuthDtos.Login("test-password","clave-nueva-12345")).accessToken()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT row_to_json(a)::text FROM API_Usuarios_Auditoria a WHERE usuario=?",String.class,id))
                .contains("CAMBIAR_PASSWORD").doesNotContain(CLAVE,"clave-nueva","password_hash");
    }
    @Test void desactivarYReactivarNoResucitaSesiones() throws Exception {
        int id=usuario("test-desactivar"); var sesion=login("test-desactivar");
        putAs("/usuarios/"+id,Map.of("usuario","test-desactivar","activo",false),token).andExpect(status().isOk());
        putAs("/usuarios/"+id+"/empresas/1/roles/"+rol("LECTURA_EMPRESA"),null,token).andExpect(status().isConflict());
        assertThatThrownBy(()->login("test-desactivar")).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
        putAs("/usuarios/"+id,Map.of("usuario","test-desactivar","activo",true),token).andExpect(status().isOk());
        getAs("/auth/me",sesion.accessToken()).andExpect(status().isUnauthorized());
        assertThat(login("test-desactivar").accessToken()).isNotBlank();
    }
    @Test void renombrarRevocaSesionYRepetirNoAudita() throws Exception {
        int id=usuario("test-renombrar"); var sesion=login("test-renombrar");
        for(int i=0;i<2;i++) putAs("/usuarios/"+id,Map.of("usuario","test-renombrado","activo",true),token).andExpect(status().isOk());
        assertThat(auditorias(id)).isEqualTo(1);
        getAs("/auth/me",sesion.accessToken()).andExpect(status().isUnauthorized());
        assertThat(login("test-renombrado").accessToken()).isNotBlank();
    }
    void unicoAdmin() {
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario<>? AND id_rol=?",admin,rol("ADMIN"));
    }
    @Test void noPermiteDesactivarUltimoAdministradorYRevierteCambio() throws Exception {
        unicoAdmin();
        putAs("/usuarios/"+admin,Map.of("usuario","test-gestion-admin","activo",false),token)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("CONFLICTO"));
        assertThat(auditorias(admin)).isZero();
    }
    @Test void noPermiteRetirarUltimoAdministrador() throws Exception {
        unicoAdmin();
        deleteAs("/usuarios/"+admin+"/roles-globales/"+rol("ADMIN"),token).andExpect(status().isConflict());
        assertThat(auditorias(admin)).isZero();
    }
    @Test void permiteRetirarAdministradorSiExisteOtroActivo() throws Exception {
        int id=usuario("test-otro-admin"); service.asignar(admin,id,null,rol("ADMIN"),true);
        String otro=login("test-otro-admin").accessToken();
        deleteAs("/usuarios/"+admin+"/roles-globales/"+rol("ADMIN"),otro).andExpect(status().isNoContent());
        getAs("/auth/me",token).andExpect(status().isUnauthorized());
    }
    @Test void rechazaEmpresaYRolInactivosPeroPermiteRevocarAsignacion() throws Exception {
        int id=usuario("test-inactivos"); int reader=rol("LECTURA_EMPRESA");
        service.asignar(admin,id,2,reader,true);
        jdbc.update("UPDATE API_Empresas SET activo=false WHERE id_empresa=2");
        putAs("/usuarios/"+id+"/empresas/2/roles/"+reader,null,token).andExpect(status().isNotFound());
        deleteAs("/usuarios/"+id+"/empresas/2/roles/"+reader,token).andExpect(status().isNoContent());
        jdbc.update("UPDATE Roles SET activo=false WHERE id_rol=?",reader);
        putAs("/usuarios/"+id+"/empresas/1/roles/"+reader,null,token).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"?page=-1","?size=101","?page=0&page=1","?desconocido=1","?page=99999999999"})
    void paginacionInvalida(String query) throws Exception { getAs("/usuarios"+query,token).andExpect(status().isBadRequest()); }
    @Test void rutasNuevasExigenSesionYNoPermitenBorrarCuentas() throws Exception {
        mvc.perform(get("/api/v1/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/usuarios").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        deleteAs("/usuarios/"+admin,token).andExpect(status().isForbidden());
        getAs("/usuarios/99999999",token).andExpect(status().isNotFound());
    }
}
