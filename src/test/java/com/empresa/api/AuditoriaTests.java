package com.empresa.api;

import com.empresa.api.auditoria.*;
import com.empresa.api.security.*;
import com.empresa.api.laboral.*;
import com.empresa.api.observaciones.*;
import com.empresa.api.facturacion.*;
import com.empresa.api.tesoreria.*;
import com.empresa.api.documentos.DocumentoService;
import com.empresa.api.maestros.MaestroService;
import org.junit.jupiter.api.*;
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
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class AuditoriaTests extends PostgreSqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired GestionUsuariosService usuarios;
    @Autowired AuditoriaService auditoria;
    @Autowired ObservacionService observaciones;
    @Autowired EmpleoService empleo;
    @Autowired FacturacionService facturas;
    @Autowired TesoreriaService tesoreria;
    @Autowired DocumentoService documentos;
    @Autowired MaestroService maestros;
    @Autowired Clock clock;
    int admin;
    String token;
    static final String CLAVE="clave-auditoria-privada";
    @BeforeEach void preparar() {
        admin=usuario("test-audit-admin","ADMIN",null);
        token=auth.login(new AuthDtos.Login("test-audit-admin",CLAVE)).accessToken();
    }
    int usuario(String nombre,String rol,Integer empresa) {
        int id=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,?) RETURNING id_usuario",Integer.class,nombre,passwords.encode(CLAVE));
        if(empresa==null) jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre=?",id,rol);
        else jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) SELECT ?,?,id_rol FROM Roles WHERE nombre=?",id,empresa,rol);
        return id;
    }
    LinkedMultiValueMap<String,String> filtro(String k,String v) {
        var q=new LinkedMultiValueMap<String,String>(); q.add(k,v);
        if(!k.equals("autor")) q.add("autor",Integer.toString(admin));
        return q;
    }
    AuditoriaDtos.Evento ultimo(String modulo) { return auditoria.listar(admin,filtro("modulo",modulo)).items().getFirst(); }

    @Test void cambiosDeCuentaSeCongelanSinCredencialesYConAnteriorConocido() throws Exception {
        var cuenta=usuarios.crear(admin,new GestionUsuariosDtos.Crear("test-audit-nueva",CLAVE));
        var crear=ultimo("USUARIOS");
        assertThat(crear.cambios().get("antes").isNull()).isTrue();
        assertThat(crear.cambios().at("/despues/activo").asBoolean()).isTrue();
        usuarios.actualizar(admin,cuenta.id(),new GestionUsuariosDtos.Actualizar(cuenta.usuario(),false));
        var actualizar=ultimo("USUARIOS");
        assertThat(actualizar.cambios().at("/antes/activo").asBoolean()).isTrue();
        assertThat(actualizar.cambios().at("/despues/activo").asBoolean()).isFalse();
        assertThat(auditoria.detalle(admin,crear.id())).isEqualTo(crear);
        usuarios.password(admin,cuenta.id(),new GestionUsuariosDtos.Password("otra-clave-secreta"));
        String body=mvc.perform(get("/api/v1/auditoria?autor="+admin).header("Authorization","Bearer "+token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(CLAVE,"otra-clave-secreta","password_hash","$2a$","test-audit-nueva");
        assertThat(ultimo("USUARIOS").cambios().at("/despues/credencialRenovada").asBoolean()).isTrue();
    }
    @Test void observacionesConservanTransicionSinCopiarTextoYNoDuplicanReintento() {
        var o=observaciones.crear(admin,1,1001,false,new ObservacionDtos.Nueva("TEXTO PRIVADO"));
        observaciones.resolver(admin,1,1001,o.id(),new ObservacionDtos.Resolver("RESOLUCION PRIVADA"));
        observaciones.resolver(admin,1,1001,o.id(),new ObservacionDtos.Resolver("RESOLUCION PRIVADA"));
        var pagina=auditoria.listar(admin,filtro("modulo","OBSERVACIONES"));
        assertThat(pagina.totalElements()).isEqualTo(2);
        var e=pagina.items().getFirst();
        assertThat(e.autorId()).isEqualTo(admin); assertThat(e.empresaId()).isEqualTo(1);
        assertThat(e.cambios().at("/antes/estado").asText()).isEqualTo("ABIERTA");
        assertThat(e.cambios().at("/despues/estado").asText()).isEqualTo("RESUELTA");
        assertThat(e.cambios().toString()).doesNotContain("PRIVAD");
    }
    @Test void maestrosLaboralYDocumentosConservanReferenciasSinContenidoPrivado() {
        var hoy=LocalDate.now(clock);
        jdbc.update("UPDATE Operarios SET fecha_alta=?,fecha_baja=NULL WHERE id_operario=301",hoy.minusYears(1));
        jdbc.update("DELETE FROM API_Cliente_Empresa WHERE id_cliente=101 AND id_empresa=2");
        maestros.compartirCliente(admin,1,101,2);
        assertThat(ultimo("MAESTROS").cambios().at("/referencias/empresaDestino").asInt()).isEqualTo(2);
        int worker=usuario("test-audit-worker","TRABAJADOR",1);
        empleo.vincular(admin,1,301,new EmpleoDtos.Vincular(worker));
        empleo.desvincular(admin,1,301);
        var asignacion=empleo.asignar(admin,1,301,new EmpleoDtos.NuevaAsignacion(1001,hoy,hoy.plusDays(1)));
        empleo.cancelarAsignacion(admin,1,301,asignacion.id());
        assertThat(ultimo("LABORAL").cambios().at("/despues/cancelada").asBoolean()).isTrue();
        var ausencia=empleo.ausencia(admin,1,301,new EmpleoDtos.NuevaAusencia(hoy,hoy,EmpleoDtos.TipoAusencia.BAJA));
        var nombre=new LinkedMultiValueMap<String,String>(); nombre.add("nombre","diagnostico-privado.pdf");
        documentos.crear(admin,1,"ausencias",ausencia.id(),"application/pdf",nombre,"%PDF-1.4\nCONTENIDO PRIVADO\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        var doc=ultimo("DOCUMENTOS");
        assertThat(doc.cambios().at("/referencias/ausenciaId").asInt()).isEqualTo(ausencia.id());
        assertThat(doc.cambios().toString()).doesNotContain("diagnostico","PRIVADO","clave","sha256","mime");
        empleo.cancelarAusencia(admin,1,301,ausencia.id());
        assertThat(ultimo("LABORAL").cambios().toString()).doesNotContain("BAJA","tipo");
    }
    @Test void facturacionYTesoreriaCapturanEstadosEImportesExactosSinMotivos() {
        var hoy=LocalDate.now(clock); var tipo=FacturacionDtos.Tipo.CLIENTE;
        var serie=facturas.crearSerie(admin,1,tipo,new FacturacionDtos.NuevaSerie("Auditoria","AU-",hoy.getYear(),6,false));
        var f=facturas.crear(admin,1,tipo,new FacturacionDtos.Nuevo(serie.id(),101,1001,hoy,null,null,
                List.of(new FacturacionDtos.EntradaLinea("PRIVADO","1","10","0","21"))));
        facturas.emitir(admin,1,tipo,f.id());
        var e=ultimo("FACTURACION");
        assertThat(e.cambios().at("/antes/estado").asText()).isEqualTo("BORRADOR");
        assertThat(e.cambios().at("/despues/estado").asText()).isEqualTo("EMITIDA");
        assertThat(e.cambios().at("/despues/total").asText()).isEqualTo("12.10");
        var plan=tesoreria.planificar(admin,1,tipo,f.id(),new TesoreriaDtos.Plan(List.of(new TesoreriaDtos.Cuota(hoy,"12.10"))));
        var m=tesoreria.registrar(admin,1,tipo,new TesoreriaDtos.NuevoMovimiento(UUID.randomUUID().toString(),hoy,"12.10","EFECTIVO",List.of(new TesoreriaDtos.Reparto(plan.getFirst().id(),"12.10"))));
        tesoreria.conciliar(admin,1,tipo,m.id(),new TesoreriaDtos.Conciliar("REFERENCIA PRIVADA"));
        tesoreria.revertir(admin,1,tipo,m.id(),new TesoreriaDtos.Revertir(hoy,"MOTIVO PRIVADO"));
        var t=ultimo("TESORERIA");
        assertThat(t.cambios().at("/antes/estado").asText()).isEqualTo("CONCILIADO");
        assertThat(t.cambios().at("/despues/estado").asText()).isEqualTo("REVERTIDO");
        assertThat(t.cambios().toString()).doesNotContain("PRIVAD");
    }
    @ParameterizedTest @ValueSource(strings={"SOCIO","LECTURA_EMPRESA","TRABAJADOR","ADMIN"})
    void lectoresYAdministradoresLocalesNoAccedenNiAlDetalle(String rol) throws Exception {
        usuarios.crear(admin,new GestionUsuariosDtos.Crear("test-audit-objetivo",CLAVE));
        long id=ultimo("USUARIOS").id();
        usuario("test-audit-denegado",rol,rol.equals("SOCIO")?null:1);
        String t=auth.login(new AuthDtos.Login("test-audit-denegado",CLAVE)).accessToken();
        for(String path:List.of("/auditoria","/auditoria?empresa=1","/auditoria/"+id))
            mvc.perform(get("/api/v1"+path).header("Authorization","Bearer "+t)).andExpect(status().isNotFound());
    }
    @ParameterizedTest @ValueSource(strings={"permiso","rol","asignacion","usuario"})
    void permisoVigenteSeRevalidaEnCadaConsulta(String cambio) throws Exception {
        switch(cambio) {
            case "permiso" -> jdbc.update("UPDATE Permisos SET activo=false WHERE nombre='AUDITORIA'");
            case "rol" -> jdbc.update("UPDATE Roles SET activo=false WHERE nombre='ADMIN'");
            case "asignacion" -> jdbc.update("DELETE FROM Usuario_Rol WHERE id_usuario=?",admin);
            case "usuario" -> jdbc.update("UPDATE Usuarios SET activo=false WHERE id_usuario=?",admin);
        }
        mvc.perform(get("/api/v1/auditoria").header("Authorization","Bearer "+token)).andExpect(status().is(cambio.equals("usuario")?401:404));
    }
    @ParameterizedTest @ValueSource(strings={"size=0","size=101","page=-1","page=100001","empresa=0","autor=no","modulo=OTRO","size=1&size=2","secreto=x","desde=ayer","hasta=2026-01-01","desde=2026-10-16T00:00:00Z&hasta=2026-10-15T00:00:00Z"})
    void filtrosInvalidosSon400(String query) throws Exception {
        mvc.perform(get("/api/v1/auditoria?"+query).header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
    }
    @Test void filtrosAislanEmpresaAutorYPeriodoConPaginacionEstable() throws Exception {
        usuarios.crear(admin,new GestionUsuariosDtos.Crear("test-audit-global",CLAVE));
        observaciones.crear(admin,1,1001,false,new ObservacionDtos.Nueva("Primera"));
        observaciones.crear(admin,1,1001,false,new ObservacionDtos.Nueva("Segunda"));
        var filtro=filtro("empresa","1"); filtro.add("size","1");
        var primera=auditoria.listar(admin,filtro); filtro.add("page","1"); var segunda=auditoria.listar(admin,filtro);
        assertThat(primera.totalElements()).isEqualTo(2); assertThat(primera.items().getFirst().id()).isGreaterThan(segunda.items().getFirst().id());
        assertThat(auditoria.listar(admin,filtro("empresa","2")).totalElements()).isZero();
        assertThat(auditoria.listar(admin,filtro("autor","2147483647")).totalElements()).isZero();
        var e=primera.items().getFirst(); var periodo=filtro("desde",e.fecha().toString());
        assertThat(auditoria.listar(admin,periodo).items()).extracting(AuditoriaDtos.Evento::id).contains(e.id());
        periodo=filtro("hasta",e.fecha().toString());
        assertThat(auditoria.listar(admin,periodo).items()).extracting(AuditoriaDtos.Evento::id).doesNotContain(e.id());
        mvc.perform(get("/api/v1/auditoria/9223372036854775807").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/auditoria")).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest @ValueSource(strings={"UPDATE API_Auditoria SET accion='OTRA'","DELETE FROM API_Auditoria"})
    void historialNoSePuedeReescribir(String sql) {
        assertThatThrownBy(()->jdbc.update(sql)).hasMessageContaining("inmutable");
    }

    @Test void falloAlConsolidarRevierteCuentaYEventoOriginal() {
        // Una colision deliberada en la proyeccion fuerza el error en el trigger.
        long siguiente=jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('api_usuarios_auditoria','id'))",Long.class)+1;
        jdbc.update("INSERT INTO API_Auditoria(modulo,origen_id,autor,empresa,entidad,entidad_id,accion,fecha,historico,cambios) VALUES ('USUARIOS',?,?,NULL,'USUARIO',?,'CREAR',CURRENT_TIMESTAMP,false,'{}')",siguiente,admin,admin);
        jdbc.execute("SAVEPOINT fallo_auditoria");
        assertThatThrownBy(()->usuarios.crear(admin,new GestionUsuariosDtos.Crear("test-audit-rollback",CLAVE)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.execute("ROLLBACK TO SAVEPOINT fallo_auditoria");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Usuarios WHERE nombre_usuario='test-audit-rollback'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM API_Usuarios_Auditoria WHERE id=?",Integer.class,siguiente)).isZero();
    }
}
