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
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class UsuariosInicialesTests extends PostgreSqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuariosInicialesService usuarios;
    @Autowired AccesoRepository acceso;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.core.env.Environment environment;
    private static final String CLAVE = "a1".repeat(32);
    private int id;

    @BeforeEach
    void preparar() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("dorna_test");
        jdbc.update("INSERT INTO Usuarios (nombre_usuario,password_hash) VALUES ('test-globales',?)", encoder.encode(CLAVE));
        id = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario='test-globales'", Integer.class);
    }
    private void asignarGlobal(String rol) {
        jdbc.update("INSERT INTO Usuario_Rol (id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre=?", id, rol);
    }
    private String token() { return auth.login(new AuthDtos.Login("test-globales", CLAVE)).accessToken(); }
    private Map<String,String> claves() {
        var result = new HashMap<String,String>();
        UsuariosInicialesService.USUARIOS.forEach(n -> result.put(n, CLAVE));
        return result;
    }

    @ParameterizedTest
    @ValueSource(strings={"ADMIN", "SOCIO"})
    void globalesLeenTodasLasEmpresasYSoloAdminPuedeEscribir(String rol) throws Exception {
        asignarGlobal(rol);
        String token = token();
        mvc.perform(get("/api/v1/empresas").header("Authorization","Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        for(int empresa=1;empresa<=4;empresa++) {
            for(String permiso : jdbc.queryForList("SELECT nombre FROM Permisos WHERE activo", String.class)) {
                assertThat(acceso.permiteLectura(id,empresa,permiso)).as(rol + ": " + permiso)
                        .isEqualTo(!permiso.equals("AUDITORIA") || rol.equals("ADMIN"));
                assertThat(acceso.permiteEscritura(id,empresa,permiso)).isEqualTo(rol.equals("ADMIN"));
            }
        }
        mvc.perform(get("/api/v1/empresas/2/clientes/102").header("Authorization","Bearer " + token)).andExpect(status().isOk());
        // Los maestros son comunes a todas las empresas activas desde V14.
        mvc.perform(get("/api/v1/empresas/1/clientes/102").header("Authorization","Bearer " + token)).andExpect(status().isOk());
        assertThat(acceso.permiteEscritura(id,999999,"CLIENTES")).isFalse();
        assertThat(acceso.permiteEscritura(id,1,"NO_EXISTE")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings={"usuario", "empresa", "rol", "permiso", "asignacion", "registro-global"})
    void revocacionGlobalSeAplicaAlMismoJwt(String caso) throws Exception {
        asignarGlobal("ADMIN");
        String token = token();
        switch(caso) {
            case "usuario" -> jdbc.update("UPDATE Usuarios SET activo=FALSE WHERE id_usuario=?",id);
            case "empresa" -> jdbc.update("UPDATE API_Empresas SET activo=FALSE WHERE id_empresa=1");
            case "rol" -> jdbc.update("UPDATE Roles SET activo=FALSE WHERE nombre='ADMIN'");
            case "permiso" -> jdbc.update("UPDATE Permisos SET activo=FALSE WHERE nombre='CLIENTES'");
            case "asignacion" -> jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",id);
            case "registro-global" -> jdbc.update("DELETE FROM API_Roles_Globales WHERE id_rol=(SELECT id_rol FROM Roles WHERE nombre='ADMIN')");
        }
        mvc.perform(get("/api/v1/empresas/1/clientes").header("Authorization","Bearer " + token))
                .andExpect(status().is(caso.equals("usuario") ? 401 : 404));
        assertThat(acceso.permiteEscritura(id,1,"CLIENTES")).isFalse();
    }

    @Test
    void socioAdminAnteriorNoObtieneAccesoGlobalNiEscritura() {
        asignarGlobal("SOCIO_ADMIN");
        assertThat(acceso.empresas(id)).isEmpty();
        jdbc.update("INSERT INTO API_Accesos_Empresa SELECT ?,1,id_rol FROM Roles WHERE nombre='SOCIO_ADMIN'",id);
        assertThat(acceso.permiteLectura(id,1,"CLIENTES")).isTrue();
        assertThat(acceso.permiteEscritura(id,1,"CLIENTES")).isFalse();
        assertThat(acceso.permiteLectura(id,2,"CLIENTES")).isFalse();
    }

    @Test
    void asignacionesLocalesYGlobalesNoDuplicanEmpresas() {
        asignarGlobal("ADMIN");
        asignarGlobal("SOCIO");
        jdbc.update("INSERT INTO API_Accesos_Empresa SELECT ?,1,id_rol FROM Roles WHERE nombre='LECTURA_EMPRESA'",id);
        assertThat(acceso.empresas(id)).hasSize(4);
    }

    @Test
    void creaLasSeisCuentasConElAmbitoAcordadoYHashes() throws Exception {
        usuarios.provisionar(claves());
        for(String nombre : UsuariosInicialesService.USUARIOS) {
            int cuenta = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario=?",Integer.class,nombre);
            String hash = jdbc.queryForObject("SELECT password_hash FROM Usuarios WHERE id_usuario=?",String.class,cuenta);
            assertThat(hash).startsWith("$2a$12$").isNotEqualTo(CLAVE);
            String token = auth.login(new AuthDtos.Login(nombre,CLAVE)).accessToken();
            Integer empresa = UsuariosInicialesService.EMPRESAS.get(nombre);
            mvc.perform(get("/api/v1/empresas").header("Authorization","Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(empresa == null ? 4 : 1));
            for(int e=1;e<=4;e++) {
                assertThat(acceso.permiteLectura(cuenta,e,"CLIENTES")).isEqualTo(empresa == null || e == empresa);
                assertThat(acceso.permiteEscritura(cuenta,e,"CLIENTES")).isEqualTo(nombre.equals("ADMIN"));
                mvc.perform(get("/api/v1/empresas/" + e).header("Authorization","Bearer " + token))
                        .andExpect(status().is(empresa == null || e == empresa ? 200 : 404));
            }
            // No abrir rutas de escritura inexistentes mediante un comodin de administrador.
            mvc.perform(post("/api/v1/empresas/1/clientes").header("Authorization","Bearer " + token)
                    .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        }
    }

    @Test
    void repetirConservaClavesIdsYSesionesSinReponerPrivilegios() {
        usuarios.provisionar(claves());
        var antes = jdbc.queryForList("SELECT id_usuario,password_hash FROM Usuarios ORDER BY id_usuario");
        auth.login(new AuthDtos.Login("ADMIN-DORNA",CLAVE));
        usuarios.provisionar(claves());
        assertThat(jdbc.queryForList("SELECT id_usuario,password_hash FROM Usuarios ORDER BY id_usuario")).isEqualTo(antes);
        int cuenta=jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario='ADMIN-DORNA'",Integer.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM API_Sesiones WHERE id_usuario=? AND NOT revocada",Integer.class,cuenta)).isEqualTo(1);
        jdbc.update("INSERT INTO Usuario_Rol (id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",cuenta);
        usuarios.provisionar(claves());
        assertThat(acceso.permiteEscritura(cuenta,1,"CLIENTES")).isFalse();
        assertThat(acceso.permiteLectura(cuenta,2,"CLIENTES")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM API_Sesiones WHERE id_usuario=? AND NOT revocada",Integer.class,cuenta)).isZero();
    }

    @Test
    void noSobrescribeClavesExistentesNiReactivaCuentas() {
        usuarios.provisionar(claves());
        var otras=claves(); otras.put("ADMIN","b2".repeat(32));
        assertThatThrownBy(() -> usuarios.provisionar(otras)).isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(CLAVE).hasMessageNotContaining(otras.get("ADMIN"));
        jdbc.update("UPDATE Usuarios SET activo=FALSE WHERE nombre_usuario='ADMIN'");
        assertThatThrownBy(() -> usuarios.provisionar(claves())).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT activo FROM Usuarios WHERE nombre_usuario='ADMIN'",Boolean.class)).isFalse();
    }

    @Test
    void entradaIncompletaOMarcadorAusenteNoCreaUsuarios() {
        assertThatThrownBy(() -> usuarios.provisionar(Map.of("ADMIN",CLAVE))).isInstanceOf(IllegalArgumentException.class);
        var cortas=claves(); cortas.put("ADMIN","debil");
        assertThatThrownBy(() -> usuarios.provisionar(cortas)).isInstanceOf(IllegalArgumentException.class);
        // El usuario de la API no puede modificar la marca. Solo el preparador
        // del ensayo la retira temporalmente y la repone incluso si falla el caso.
        var preparador = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                environment.getRequiredProperty("spring.datasource.url"),
                environment.getRequiredProperty("dorna.test.admin-user"),
                environment.getRequiredProperty("dorna.test.admin-password")));
        String signature = preparador.queryForObject("SELECT signature FROM dorna_demo_metadata WHERE id=1", String.class);
        try {
            preparador.update("DELETE FROM dorna_demo_metadata WHERE id=1");
            assertThatThrownBy(() -> usuarios.provisionar(claves())).isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM Usuarios WHERE nombre_usuario='ADMIN'",Integer.class)).isZero();
        } finally {
            preparador.update("INSERT INTO dorna_demo_metadata(id,signature) VALUES (1,?)", signature);
        }
    }
}
