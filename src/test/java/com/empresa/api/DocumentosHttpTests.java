package com.empresa.api;

import com.empresa.api.documentos.ArchivoStore;
import com.empresa.api.security.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"server.address=127.0.0.1","dorna.limits.enabled=true","dorna.limits.requests-per-minute=1000","dorna.limits.writes-per-minute=100"})
@ActiveProfiles("test")
class DocumentosHttpTests extends PostgreSqlIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ArchivoStore store;
    @Autowired ObjectMapper json;
    @Test void transporteRealConLongitudYChunkedConservaBytesYLimites() throws Exception {
        String nombre="test-doc-http-"+UUID.randomUUID();
        int admin=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,?) RETURNING id_usuario",Integer.class,nombre,passwords.encode("clave-prueba-http"));
        jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre='ADMIN'",admin);
        try(var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            String token=auth.login(new AuthDtos.Login(nombre,"clave-prueba-http")).accessToken();
            String base="http://127.0.0.1:"+port+"/api/v1/empresas/1/documentos/obras/1001";
            byte[] bytes=("%PDF-1.7\n"+" ".repeat(32768)+"\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
            for(boolean chunked:new boolean[]{false,true}) {
                var body=chunked?HttpRequest.BodyPublishers.ofInputStream(()->new java.io.ByteArrayInputStream(bytes)):HttpRequest.BodyPublishers.ofByteArray(bytes);
                var upload=HttpRequest.newBuilder(URI.create(base+"?nombre=prueba.pdf")).timeout(Duration.ofSeconds(10))
                        .header("Authorization","Bearer "+token).header("Content-Type","application/pdf; charset=UTF-8").POST(body).build();
                var response=client.send(upload,HttpResponse.BodyHandlers.ofByteArray());
                assertThat(response.statusCode()).isEqualTo(201);
                int id=json.readTree(response.body()).get("id").asInt();
                var download=client.send(HttpRequest.newBuilder(URI.create(base+"/"+id+"/contenido")).header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
                assertThat(download.statusCode()).isEqualTo(200); assertThat(download.body()).containsExactly(bytes);
                assertThat(download.headers().firstValue("Content-Disposition").orElseThrow()).startsWith("attachment;");
            }
            var rejected=client.send(HttpRequest.newBuilder(URI.create(base+"?nombre=prueba.pdf")).header("Authorization","Bearer "+token)
                    .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(rejected.statusCode()).isEqualTo(413);
            assertThat(json.readTree(rejected.body()).get("codigo").asText()).isEqualTo("CUERPO_DEMASIADO_GRANDE");
        } finally {
            jdbc.queryForList("SELECT clave FROM API_Documentos WHERE autor=?",String.class,admin).forEach(store::borrar);
            jdbc.update("DELETE FROM API_Documentos_Auditoria WHERE autor=?",admin);
            jdbc.update("DELETE FROM API_Documentos WHERE autor=?",admin);
            jdbc.update("DELETE FROM API_Sesiones WHERE id_usuario=?",admin);
            jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",admin);
            jdbc.update("DELETE FROM Usuarios WHERE id_usuario=?",admin);
        }
    }
}
