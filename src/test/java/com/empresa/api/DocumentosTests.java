package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.documentos.*;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import static com.empresa.api.laboral.EmpleoDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class DocumentosTests extends PostgreSqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired DocumentoService service;
    @Autowired ArchivoStore store;
    @Autowired EmpleoService empleo;
    @Autowired Clock clock;
    int admin,worker,lector,ausencia;
    String token,bearer,lectura;
    static final String BASE="/api/v1/empresas/1/documentos/obras/1001";
    static final byte[] PDF=("%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    @BeforeEach void preparar() {
        admin=usuario("test-doc-admin","ADMIN",null);
        worker=usuario("test-doc-worker","TRABAJADOR",1);
        lector=usuario("test-doc-lector","LECTURA_EMPRESA",1);
        var hoy=LocalDate.now(clock);
        jdbc.update("UPDATE Operarios SET fecha_alta=?,fecha_baja=NULL WHERE id_operario=301",hoy.minusYears(1));
        empleo.vincular(admin,1,301,new Vincular(worker));
        ausencia=empleo.ausencia(admin,1,301,new NuevaAusencia(hoy,hoy,TipoAusencia.PERMISO)).id();
        token=login("test-doc-admin"); bearer=login("test-doc-worker"); lectura=login("test-doc-lector");
    }
    int usuario(String nombre,String rol,Integer empresa) {
        int id=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,?) RETURNING id_usuario",Integer.class,nombre,passwords.encode("clave-documentos-prueba"));
        if(empresa==null) jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre=?",id,rol);
        else jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) SELECT ?,?,id_rol FROM Roles WHERE nombre=?",id,empresa,rol);
        return id;
    }
    String login(String nombre) { return auth.login(new AuthDtos.Login(nombre,"clave-documentos-prueba")).accessToken(); }
    static LinkedMultiValueMap<String,String> nombre(String nombre) { var q=new LinkedMultiValueMap<String,String>();q.add("nombre",nombre);return q; }
    DocumentoDtos.Detalle crear(String tipo,int referencia) { return service.crear(admin,1,tipo,referencia,"application/pdf",nombre("informe.pdf"),PDF); }
    @Test void subidaBinariaMetadatosDescargaYAuditoria() throws Exception {
        var response=mvc.perform(post(BASE).header("Authorization","Bearer "+token).param("nombre","informe.pdf").contentType("application/pdf").content(PDF))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value("informe.pdf"))
                .andExpect(jsonPath("$.tamano").value(PDF.length)).andExpect(jsonPath("$.sha256").value(DocumentoValidacion.hash(PDF)))
                .andExpect(jsonPath("$.autorId").value(admin)).andExpect(jsonPath("$.clave").doesNotExist())
                .andExpect(jsonPath("$.proveedor").doesNotExist()).andReturn().getResponse().getContentAsString();
        int id=json.readTree(response).get("id").asInt();
        mvc.perform(get(BASE+"/"+id+"/contenido").header("Authorization","Bearer "+lectura)).andExpect(status().isOk())
                .andExpect(content().bytes(PDF)).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(header().string("Content-Security-Policy","sandbox"));
        assertThat(jdbc.queryForObject("SELECT autor FROM API_Documentos_Auditoria WHERE documento=?",Integer.class,id)).isEqualTo(admin);
    }
    @Test void listadoPaginadoYContratoDeConsulta() throws Exception {
        crear("obras",1001); var segundo=crear("obras",1001);
        mvc.perform(get(BASE+"?size=1&page=1").header("Authorization","Bearer "+lectura)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(segundo.id())).andExpect(jsonPath("$.totalElements").value(2));
        for(String query:new String[]{"size=101","page=-1","page=1&page=2","clave=x"})
            mvc.perform(get(BASE+"?"+query).header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
    }
    @Test void presupuestosTienenAsociacionYPermisoPropios() {
        int presupuesto=jdbc.queryForObject("SELECT id_presupuesto FROM API_Presupuesto_Empresa WHERE id_empresa=1 ORDER BY id_presupuesto LIMIT 1",Integer.class);
        var d=crear("presupuestos",presupuesto);
        assertThat(service.descargar(lector,1,"presupuestos",presupuesto,d.id(),false).bytes()).isEqualTo(PDF);
        assertThatThrownBy(()->service.consultar(admin,1,"obras",1001,d.id(),false)).isInstanceOf(ApiException.class);
    }
    @Test void justificanteSoloAdministracionYTItular() throws Exception {
        var d=crear("ausencias",ausencia);
        String propia="/api/v1/empresas/1/mi-trabajo/ausencias/"+ausencia+"/justificantes";
        mvc.perform(get(propia).header("Authorization","Bearer "+bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get(propia+"/"+d.id()+"/contenido").header("Authorization","Bearer "+bearer)).andExpect(status().isOk()).andExpect(content().bytes(PDF));
        mvc.perform(get("/api/v1/empresas/1/documentos/ausencias/"+ausencia).header("Authorization","Bearer "+lectura)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/empresas/1/documentos/ausencias/"+ausencia).header("Authorization","Bearer "+bearer)).andExpect(status().isNotFound());
        mvc.perform(get(propia).header("Authorization","Bearer "+lectura)).andExpect(status().isNotFound());
        mvc.perform(post(propia).header("Authorization","Bearer "+bearer).contentType("application/pdf").content(PDF)).andExpect(status().isForbidden());
        empleo.desvincular(admin,1,301);
        assertThatThrownBy(()->service.consultar(worker,1,"ausencias",ausencia,d.id(),true)).isInstanceOf(ApiException.class);
    }
    @Test void ausenciaAjenaNoSeEnumeraYCancelacionConservaHistorial() {
        var d=crear("ausencias",ausencia);
        int otra=jdbc.queryForObject("INSERT INTO API_Ausencias(id_empresa,id_operario,desde,hasta,tipo,cancelada) SELECT id_empresa,id_operario,CURRENT_DATE,CURRENT_DATE,'PERMISO',FALSE FROM API_Operario_Empresa WHERE id_operario<>301 ORDER BY id_operario LIMIT 1 RETURNING id",Integer.class);
        assertThatThrownBy(()->service.listar(worker,1,"ausencias",otra,true,new LinkedMultiValueMap<>())).isInstanceOf(ApiException.class);
        empleo.cancelarAusencia(admin,1,301,ausencia);
        assertThat(service.descargar(worker,1,"ausencias",ausencia,d.id(),true).bytes()).isEqualTo(PDF);
        assertThatThrownBy(()->crear("ausencias",ausencia)).isInstanceOf(ApiException.class);
    }
    @Test void empresaRecursoYPermisosSeRevalidan() throws Exception {
        var d=crear("obras",1001);
        for(String path:new String[]{BASE.replace("empresas/1","empresas/2")+"/"+d.id(),BASE.replace("1001","1003")+"/"+d.id(),BASE.replace("obras","desconocido")})
            mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        mvc.perform(get(BASE).header("Authorization","Bearer "+bearer)).andExpect(status().isNotFound());
        mvc.perform(post(BASE).header("Authorization","Bearer "+lectura).param("nombre","informe.pdf").contentType("application/pdf").content(PDF)).andExpect(status().isNotFound());
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        jdbc.update("DELETE FROM Rol_Permiso WHERE id_permiso=(SELECT id_permiso FROM Permisos WHERE nombre='DOCUMENTOS')");
        assertThatThrownBy(()->service.descargar(admin,1,"obras",1001,d.id(),false)).isInstanceOf(ApiException.class);
    }
    @ParameterizedTest @ValueSource(strings={"../informe.pdf","C:\\informe.pdf","informe\n.pdf"," informe.pdf","informe.exe","informe.pdf\u202e"})
    void rechazaNombresInseguros(String nombre) throws Exception {
        mvc.perform(post(BASE).header("Authorization","Bearer "+token).param("nombre",nombre).contentType("application/pdf").content(PDF)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Documentos",Integer.class)).isZero();
    }
    @Test void rechazaContenidoFalsoVacioGrandeYParametrosExtra() throws Exception {
        for(byte[] bytes:new byte[][]{"<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8),new byte[0]})
            mvc.perform(post(BASE).header("Authorization","Bearer "+token).param("nombre","informe.pdf").contentType("application/pdf").content(bytes)).andExpect(status().isBadRequest());
        mvc.perform(post(BASE).header("Authorization","Bearer "+token).param("nombre","informe.pdf").contentType("application/pdf").content(new byte[DocumentoValidacion.MAX_BYTES+1])).andExpect(status().isPayloadTooLarge());
        mvc.perform(post(BASE).header("Authorization","Bearer "+token).param("nombre","informe.pdf").param("autorId","1").contentType("application/pdf").content(PDF)).andExpect(status().isBadRequest());
        mvc.perform(post(BASE).header("Authorization","Bearer "+token).param("nombre","informe.pdf").contentType("text/html").content(PDF)).andExpect(status().isUnsupportedMediaType());
    }
    @Test void archivoAusenteOAlteradoNuncaSeEntrega() {
        var d=crear("obras",1001);
        String clave=jdbc.queryForObject("SELECT clave FROM API_Documentos WHERE id=?",String.class,d.id());
        store.borrar(clave); store.guardar(clave,"alterado".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(()->service.descargar(admin,1,"obras",1001,d.id(),false)).isInstanceOf(ApiException.class).extracting("status").isEqualTo(503);
        store.borrar(clave);
        assertThatThrownBy(()->service.descargar(admin,1,"obras",1001,d.id(),false)).isInstanceOf(ApiException.class).extracting("status").isEqualTo(404);
    }
}
