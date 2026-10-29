package com.empresa.api;

import com.empresa.api.security.AuthDtos;
import com.empresa.api.security.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AuthRolesTests extends PostgreSqlIntegrationTest {
    private static final String CLAVE = "clave-ficticia-12345";
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @Autowired MockMvc mvc;
    int usuario;

    @BeforeEach
    void preparar() {
        usuario = crear("test-auth-roles");
    }

    int crear(String nombre) {
        return jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,?) RETURNING id_usuario",
                Integer.class, nombre, passwords.encode(CLAVE));
    }

    int rol(String nombre) {
        return jdbc.queryForObject("SELECT id_rol FROM Roles WHERE nombre=?", Integer.class, nombre);
    }

    String token(String nombre) {
        return auth.login(new AuthDtos.Login(nombre, CLAVE)).accessToken();
    }

    @Test
    void usuarioNormalConsultaSoloSusRolesLocalesConIdsYNombres() throws Exception {
        int lectura = rol("LECTURA_EMPRESA");
        int ajeno = crear("test-auth-roles-ajeno");
        jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) VALUES (?,?,?),(?,?,?)",
                usuario, 1, lectura, ajeno, 2, lectura);

        mvc.perform(get("/api/v1/auth/me/roles").header("Authorization", "Bearer " + token("test-auth-roles")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].empresa").value(1))
                .andExpect(jsonPath("$[0].rol").value(lectura))
                .andExpect(jsonPath("$[0].nombre").value("LECTURA_EMPRESA"))
                .andExpect(jsonPath("$[0].global").value(false));
    }

    @Test
    void administradorConsultaSuRolGlobal() throws Exception {
        int admin = rol("ADMIN");
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) VALUES (?,?)", usuario, admin);

        mvc.perform(get("/api/v1/auth/me/roles").header("Authorization", "Bearer " + token("test-auth-roles")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].empresa").isEmpty())
                .andExpect(jsonPath("$[0].rol").value(admin))
                .andExpect(jsonPath("$[0].nombre").value("ADMIN"))
                .andExpect(jsonPath("$[0].global").value(true));
    }

    @Test
    void combinaRolesGlobalesYPorEmpresa() throws Exception {
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) VALUES (?,?)", usuario, rol("SOCIO"));
        jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) VALUES (?,?,?)",
                usuario, 2, rol("TRABAJADOR"));

        mvc.perform(get("/api/v1/auth/me/roles").header("Authorization", "Bearer " + token("test-auth-roles")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].global").value(true))
                .andExpect(jsonPath("$[1].empresa").value(2))
                .andExpect(jsonPath("$[1].global").value(false));
    }

    @Test
    void sinRolesDevuelveListaVacia() throws Exception {
        mvc.perform(get("/api/v1/auth/me/roles").header("Authorization", "Bearer " + token("test-auth-roles")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void omiteAsignacionesInactivas() throws Exception {
        int lectura = rol("LECTURA_EMPRESA");
        jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) VALUES (?,?,?)", usuario, 1, lectura);
        jdbc.update("UPDATE API_Empresas SET activo=false WHERE id_empresa=1");
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) VALUES (?,?)", usuario, rol("SOCIO"));
        jdbc.update("UPDATE Roles SET activo=false WHERE nombre='SOCIO'");

        mvc.perform(get("/api/v1/auth/me/roles").header("Authorization", "Bearer " + token("test-auth-roles")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void exigeAutenticacion() throws Exception {
        mvc.perform(get("/api/v1/auth/me/roles")).andExpect(status().isUnauthorized());
    }
}
