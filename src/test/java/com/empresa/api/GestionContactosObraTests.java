package com.empresa.api;

import com.empresa.api.gestion.GestionContactosObraService;
import com.empresa.api.security.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import static com.empresa.api.gestion.GestionContactosObraDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class GestionContactosObraTests extends PostgreSqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired GestionContactosObraService service;
    String token;
    int usuario;
    static final String BASE="/api/v1/empresas/1/gestion/obras/1001/contactos";
    @BeforeEach void preparar() {
        usuario=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES ('test-contacto-obra',?) RETURNING id_usuario",Integer.class,passwords.encode("clave-contacto"));
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",usuario);
        token=auth.login(new AuthDtos.Login("test-contacto-obra","clave-contacto")).accessToken();
    }
    ResultActions crear(String ruta,Object datos) throws Exception {
        return mvc.perform(post(ruta).header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsBytes(datos)));
    }
    ResultActions editar(int id,Object datos) throws Exception {
        return mvc.perform(put(BASE+"/"+id).header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsBytes(datos)));
    }
    int asignar(String rol) throws Exception {
        return json.readTree(crear(BASE,new Asignar(101,rol," Nota privada ")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asInt();
    }
    @Test void cicloCompletoConservaContactoPrincipalYOtrasObrasYAuditaSinTextoPrivado() throws Exception {
        var contacto=jdbc.queryForMap("SELECT * FROM Contactos WHERE id_contacto=101");
        int id=asignar(" Responsable ");
        service.asignar(usuario,1,1002,new Asignar(101,"Otra obra",null));
        mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("Responsable")).andExpect(jsonPath("$.observaciones").value("Nota privada"));
        mvc.perform(get("/api/v1/empresas/1/obras/1001/contactos").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].contacto.principal").value(true))
                .andExpect(jsonPath("$.items[0].observaciones").doesNotExist());
        editar(id,new Editar("Coordinador"," Modificada ")).andExpect(status().isOk()).andExpect(jsonPath("$.observaciones").value("Modificada"));
        editar(id,new Editar("Coordinador","Modificada")).andExpect(status().isOk());
        mvc.perform(delete(BASE+"/"+id).header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        mvc.perform(delete(BASE+"/"+id).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForMap("SELECT * FROM Contactos WHERE id_contacto=101")).isEqualTo(contacto);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Obra_Contacto WHERE id_obra=1002 AND id_contacto=101",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT accion FROM API_Auditoria WHERE autor=? AND entidad='OBRA' AND entidad_id=1001 ORDER BY id",String.class,usuario))
                .containsExactly("CONTACTO_ASIGNAR","CONTACTO_MODIFICAR","CONTACTO_DESVINCULAR");
        assertThat(jdbc.queryForList("SELECT cambios::text FROM API_Auditoria WHERE autor=?",String.class,usuario).toString())
                .doesNotContain("Nota privada","\"Modificada\"","Responsable","Coordinador");
    }
    @Test void duplicadosRespetanCollationYPermitenRolesDistintos() throws Exception {
        asignar("Técnico");
        crear(BASE,new Asignar(101," tecnico ",null)).andExpect(status().isConflict());
        int segundo=asignar("Responsable");
        editar(segundo,new Editar("TECNICO",null)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT rol_en_obra FROM Obra_Contacto WHERE id_obra_contacto=?",String.class,segundo)).isEqualTo("Responsable");
    }
    @ParameterizedTest @ValueSource(strings={"LECTURA_EMPRESA","TRABAJADOR"})
    void lectoresNoGestionan(String rol) throws Exception {
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",usuario);
        jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) SELECT ?,1,id_rol FROM Roles WHERE nombre=?",usuario,rol);
        crear(BASE,new Asignar(101,"Rol",null)).andExpect(status().isNotFound());
        editar(1,new Editar("Rol",null)).andExpect(status().isNotFound());
        mvc.perform(get(BASE+"/1").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        mvc.perform(delete(BASE+"/1").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
    }
    @Test void escritorLocalPuedeGestionarSoloSuEmpresa() throws Exception {
        jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",usuario);
        int rol=jdbc.queryForObject("INSERT INTO Roles(nombre) VALUES ('TEST_CONTACTOS_ESCRITOR') RETURNING id_rol",Integer.class);
        jdbc.update("INSERT INTO Rol_Permiso(id_rol,id_permiso,nivel_acceso) SELECT ?,id_permiso,CASE WHEN nombre='OBRAS' THEN 'ESCRITURA' ELSE 'LECTURA' END FROM Permisos WHERE nombre IN ('EMPRESA','OBRAS','CLIENTES')",rol);
        jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) VALUES (?,1,?)",usuario,rol);
        asignar("Local");
        crear(BASE.replace("empresas/1","empresas/2").replace("1001","1003"),new Asignar(101,"Rol",null)).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"CLIENTES","OBRAS","EMPRESA"})
    void exigeTodosLosPermisos(String permiso) throws Exception {
        jdbc.update("DELETE FROM Rol_Permiso WHERE id_rol=(SELECT id_rol FROM Roles WHERE nombre='ADMIN') AND id_permiso=(SELECT id_permiso FROM Permisos WHERE nombre=?)",permiso);
        crear(BASE,new Asignar(101,"Rol",null)).andExpect(status().isNotFound());
    }
    @Test void rechazaOtraEmpresaAunqueClienteSeaCompartido() throws Exception {
        jdbc.update("INSERT INTO API_Cliente_Empresa VALUES (101,2) ON CONFLICT DO NOTHING");
        crear(BASE.replace("empresas/1","empresas/2"),new Asignar(101,"Rol",null)).andExpect(status().isNotFound());
    }
    @Test void exigeVisibilidadDelClienteYContactoDeEseCliente() throws Exception {
        int otro=jdbc.queryForObject("INSERT INTO Contactos(id_entidad,nombre) VALUES (102,'Otro') RETURNING id_contacto",Integer.class);
        crear(BASE,new Asignar(otro,"Rol",null)).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_empresa=1 AND id_cliente=101");
        crear(BASE,new Asignar(101,"Rol",null)).andExpect(status().isNotFound());
    }
    @Test void inactivosNoSeAsignanPeroSePuedenDesvincular() throws Exception {
        int id=asignar("Retirar");
        jdbc.update("UPDATE Contactos SET activo=false WHERE id_contacto=101"); em.clear();
        crear(BASE,new Asignar(101,"Otro",null)).andExpect(status().isConflict());
        editar(id,new Editar("Otro",null)).andExpect(status().isConflict());
        mvc.perform(delete(BASE+"/"+id).header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Contactos WHERE id_contacto=101",Integer.class)).isEqualTo(1);
    }
    @Test void clienteInactivoNoAdmiteAsignacion() throws Exception {
        jdbc.update("UPDATE Entidades SET activo=false WHERE id_entidad=101");
        crear(BASE,new Asignar(101,"Rol",null)).andExpect(status().isConflict());
    }
    @Test void inexistentesYRelacionDeOtraObraDevuelven404() throws Exception {
        crear(BASE.replace("1001","999999"),new Asignar(101,"Rol",null)).andExpect(status().isNotFound());
        crear(BASE,new Asignar(999999,"Rol",null)).andExpect(status().isNotFound());
        editar(999999,new Editar("Rol",null)).andExpect(status().isNotFound());
        int otra=service.asignar(usuario,1,1002,new Asignar(101,"Otra",null)).id();
        editar(otra,new Editar("Rol",null)).andExpect(status().isNotFound());
        mvc.perform(get(BASE+"/"+otra).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        mvc.perform(delete(BASE+"/"+otra).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
    }
    @Test void validaCuerposYNormalizaObservacionesOpcionales() throws Exception {
        for(var datos: new Object[]{Map.of(),new Asignar(0,"Rol",null),new Asignar(101," ",null),
                new Asignar(101,"x".repeat(81),null),new Asignar(101,"Rol","x".repeat(1001))})
            crear(BASE,datos).andExpect(status().isBadRequest());
        int id=asignar("Valido");
        editar(id,new Editar(" ",null)).andExpect(status().isBadRequest());
        editar(id,new Editar("Valido"," ")).andExpect(status().isOk()).andExpect(jsonPath("$.observaciones").isEmpty());
    }
    @Test void requiereJwt() throws Exception {
        mvc.perform(post(BASE).contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
    }
}
