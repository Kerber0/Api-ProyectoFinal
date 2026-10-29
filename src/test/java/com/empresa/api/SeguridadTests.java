package com.empresa.api;

import com.empresa.api.dto.UsuarioCreateDTO;
import com.empresa.api.dto.UsuarioUpdateDTO;
import com.empresa.api.security.AuthDtos;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class SeguridadTests extends PostgreSqlIntegrationTest {
    private static final String PASSWORD = "Clave-sintetica-solo-para-pruebas-2026";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder jwtEncoder;
    private int usuario;

    @BeforeEach
    void prepararCuentaAislada() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("dorna_test");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dorna_demo_metadata WHERE id = 1", Integer.class)).isEqualTo(1);
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-seguridad', ?)", encoder.encode(PASSWORD));
        usuario = jdbc.queryForObject("SELECT id_usuario FROM Usuarios WHERE nombre_usuario = 'test-seguridad'", Integer.class);
        jdbc.update("""
                INSERT INTO API_Accesos_Empresa (id_usuario, id_empresa, id_rol)
                SELECT ?, 1, id_rol FROM Roles WHERE nombre = 'CONSULTA_EMPRESA_DEMO'
                """, usuario);
    }

    private AuthDtos.Tokens login() throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(new AuthDtos.Login("test-seguridad", PASSWORD))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        return json.readValue(result, AuthDtos.Tokens.class);
    }

    private String bodyRefresh(String refresh) { return json.writeValueAsString(new AuthDtos.Refresh(refresh)); }

    @Test
    void sinCredencialesNiBasicNoSeAccede() throws Exception {
        mvc.perform(get("/api/v1/empresas")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(get("/api/v1/empresas").header("Authorization", "Basic ZGVtbzpkZW1v"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginDevuelvePerfilMinimoYTokensSinGuardarRefreshEnClaro() throws Exception {
        var tokens = login();
        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        assertThat(tokens.expiresIn()).isBetween(599L, 600L);
        String hash = jdbc.queryForObject("SELECT refresh_hash FROM API_Sesiones WHERE id_usuario = ?", String.class, usuario);
        assertThat(hash).hasSize(64).isNotEqualTo(tokens.refreshToken());
        assertThat(jdbc.queryForObject("SELECT password_hash FROM Usuarios WHERE id_usuario = ?", String.class, usuario))
                .startsWith("$2a$12$").isNotEqualTo(PASSWORD);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(usuario))
                .andExpect(jsonPath("$.usuario").value("test-seguridad"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void soloListaEmpresaAutorizadaYOcultaLasDemas() throws Exception {
        var tokens = login();
        mvc.perform(get("/api/v1/empresas").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));
        mvc.perform(get("/api/v1/empresas/1").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());
        String oculto = mvc.perform(get("/api/v1/empresas/2").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/empresas/999999").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(json.readTree(oculto).get("codigo").asText()))
                .andExpect(jsonPath("$.detalle").value(json.readTree(oculto).get("detalle").asText()));
    }

    @Test
    void retirarAccesoTieneEfectoConElMismoJwt() throws Exception {
        var tokens = login();
        jdbc.update("DELETE FROM API_Accesos_Empresa WHERE id_usuario = ?", usuario);
        mvc.perform(get("/api/v1/empresas").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/empresas/1").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void rolGlobalAdminNoConcedeEmpresasNiPermiteEscribir() throws Exception {
        jdbc.update("INSERT INTO Roles (nombre) VALUES ('TEST_ADMIN_GLOBAL')");
        jdbc.update("""
                INSERT INTO Rol_Permiso (id_rol, id_permiso, nivel_acceso)
                SELECT r.id_rol, p.id_permiso, 'ADMIN' FROM Roles r CROSS JOIN Permisos p
                WHERE r.nombre = 'TEST_ADMIN_GLOBAL' AND p.nombre = 'EMPRESA'
                """);
        jdbc.update("""
                INSERT INTO Usuario_Rol (id_usuario, id_rol)
                SELECT ?, id_rol FROM Roles WHERE nombre = 'TEST_ADMIN_GLOBAL'
                """, usuario);
        jdbc.update("DELETE FROM API_Accesos_Empresa WHERE id_usuario = ?", usuario);
        var tokens = login();
        mvc.perform(get("/api/v1/empresas").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/v1/empresas").header("Authorization", "Bearer " + tokens.accessToken())
                        .contentType("application/json").content("{\"id\":2}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/empresas/1/obras/1001").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documentos/1").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void rolDesactivadoNoConcedeAcceso() throws Exception {
        var tokens = login();
        jdbc.update("UPDATE Roles SET activo = FALSE WHERE nombre = 'CONSULTA_EMPRESA_DEMO'");
        mvc.perform(get("/api/v1/empresas/1").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void permisoDeUnaEmpresaNoSeExtiendeAlRolDeOtra() throws Exception {
        jdbc.update("INSERT INTO Roles (nombre) VALUES ('TEST_SIN_PERMISOS')");
        jdbc.update("""
                INSERT INTO API_Accesos_Empresa (id_usuario, id_empresa, id_rol)
                SELECT ?, 2, id_rol FROM Roles WHERE nombre = 'TEST_SIN_PERMISOS'
                """, usuario);
        var tokens = login();
        mvc.perform(get("/api/v1/empresas").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(1));
        mvc.perform(get("/api/v1/empresas/2").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void otroUsuarioSoloAccedeASuPropiaEmpresa() throws Exception {
        jdbc.update("INSERT INTO Usuarios (nombre_usuario, password_hash) VALUES ('test-otro', ?)", encoder.encode(PASSWORD));
        jdbc.update("""
                INSERT INTO API_Accesos_Empresa (id_usuario, id_empresa, id_rol)
                SELECT u.id_usuario, 2, r.id_rol FROM Usuarios u CROSS JOIN Roles r
                WHERE u.nombre_usuario = 'test-otro' AND r.nombre = 'CONSULTA_EMPRESA_DEMO'
                """);
        String response = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(new AuthDtos.Login("test-otro", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var tokens = json.readValue(response, AuthDtos.Tokens.class);
        mvc.perform(get("/api/v1/empresas").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(2));
        mvc.perform(get("/api/v1/empresas/1").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void permisoDesactivadoNoConcedeAcceso() throws Exception {
        var tokens = login();
        jdbc.update("UPDATE Permisos SET activo = FALSE WHERE nombre = 'EMPRESA'");
        mvc.perform(get("/api/v1/empresas/1").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void empresaDesactivadaNoSeLista() throws Exception {
        var tokens = login();
        jdbc.update("UPDATE API_Empresas SET activo = FALSE WHERE id_empresa = 1");
        mvc.perform(get("/api/v1/empresas").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void credencialesIncorrectasYUsuarioInexistenteDanMismoError() throws Exception {
        String error = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(new AuthDtos.Login("test-seguridad", "incorrecta"))))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(new AuthDtos.Login("no-existe", "incorrecta"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value(json.readTree(error).get("codigo").asText()))
                .andExpect(jsonPath("$.detalle").value(json.readTree(error).get("detalle").asText()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM API_Sesiones WHERE id_usuario = ?", Integer.class, usuario)).isZero();
    }

    @Test
    void usuarioDesactivadoNoAccedeNiRenuevaNiIniciaSesion() throws Exception {
        var tokens = login();
        jdbc.update("UPDATE Usuarios SET activo = FALSE WHERE id_usuario = ?", usuario);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(bodyRefresh(tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(new AuthDtos.Login("test-seguridad", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevocaAccessYRefreshPeroNoOtrasSesiones() throws Exception {
        var primera = login();
        var segunda = login();
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + primera.accessToken()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + primera.accessToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(bodyRefresh(primera.refreshToken())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + segunda.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void renovacionInvalidaAmbosTokensAnterioresYMantieneLimiteDeSesion() throws Exception {
        var anterior = login();
        Timestamp limite = jdbc.queryForObject("SELECT expira FROM API_Sesiones WHERE id_usuario = ?", Timestamp.class, usuario);
        String response = mvc.perform(post("/api/v1/auth/refresh").contentType("application/json")
                        .content(bodyRefresh(anterior.refreshToken())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var nuevo = json.readValue(response, AuthDtos.Tokens.class);
        assertThat(nuevo.refreshToken()).isNotEqualTo(anterior.refreshToken());
        assertThat(jdbc.queryForObject("SELECT expira FROM API_Sesiones WHERE id_usuario = ?", Timestamp.class, usuario)).isEqualTo(limite);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + nuevo.accessToken()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + anterior.accessToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(bodyRefresh(anterior.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sesionVencidaNoAceptaAccessNiRefresh() throws Exception {
        var tokens = login();
        jdbc.update("UPDATE API_Sesiones SET expira = ? WHERE id_usuario = ?", Timestamp.from(Instant.now().minusSeconds(60)), usuario);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(bodyRefresh(tokens.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void jwtVencidoAudienciaEmisorFirmaOSujetoIncorrectosSeRechazan() throws Exception {
        var token = decoder.decode(login().accessToken());
        var original = JwtClaimsSet.builder().claims(claims -> claims.putAll(token.getClaims())).build();
        var vencido = JwtClaimsSet.builder().claims(claims -> claims.putAll(token.getClaims()))
                .issuedAt(Instant.now().minusSeconds(200)).notBefore(Instant.now().minusSeconds(200))
                .expiresAt(Instant.now().minusSeconds(100)).build();
        var audiencia = JwtClaimsSet.builder().claims(claims -> claims.putAll(token.getClaims())).audience(List.of("otra-api")).build();
        var emisor = JwtClaimsSet.builder().claims(claims -> claims.putAll(token.getClaims())).issuer("otro-emisor").build();
        var sujeto = JwtClaimsSet.builder().claims(claims -> claims.putAll(token.getClaims())).subject("999999").build();
        var sinExpiracion = JwtClaimsSet.builder().claims(claims -> { claims.putAll(token.getClaims()); claims.remove("exp"); }).build();
        for (var claims : List.of(vencido, audiencia, emisor, sujeto, sinExpiracion)) {
            String jwt = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());
        }
        var otraFirma = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(new byte[32], "HmacSHA256")));
        String firmado = otraFirma.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), original)).getTokenValue();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + firmado)).andExpect(status().isUnauthorized());
    }

    @Test
    void solicitudesMalformadasNoFiltranCredencialesNiErroresInternos() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"usuario\":\"\",\"password\":\"secreto\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.trace").doesNotExist());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content("{no-json"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/refresh").contentType("application/json").content(bodyRefresh("inventado")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dtosNoExponenContrasenasAlSerializarNiEnToString() {
        var alta = UsuarioCreateDTO.builder().password(PASSWORD).build();
        var cambio = UsuarioUpdateDTO.builder().password(PASSWORD).build();
        assertThat(alta.toString()).doesNotContain(PASSWORD);
        assertThat(cambio.toString()).doesNotContain(PASSWORD);
        assertThat(json.writeValueAsString(alta)).doesNotContain(PASSWORD, "password");
        assertThat(json.writeValueAsString(cambio)).doesNotContain(PASSWORD, "password");
        assertThat(new AuthDtos.Login("demo", PASSWORD).toString()).doesNotContain(PASSWORD);
    }
}
