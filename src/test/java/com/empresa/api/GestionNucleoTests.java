package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.gestion.*;
import com.empresa.api.maestros.MaestroService;
import com.empresa.api.security.*;
import com.empresa.api.laboral.*;
import com.empresa.api.facturacion.FacturacionDtos.EntradaLinea;
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
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import static com.empresa.api.gestion.GestionDtos.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class GestionNucleoTests extends PostgreSqlIntegrationTest {
    // Fixture sintético determinista: BBAN de prueba y checksum calculado localmente; no procede de una cuenta conocida.
    private static final String IBAN_SINTETICO = "GB05TEST00000000005432";
    private static final String IBAN_SINTETICO_FORMATADO = "gb05 test 0000 0000 0054 32";
    @Autowired GestionMaestrosService maestros;
    @Autowired GestionObrasService obras;
    @Autowired MaestroService compartir;
    @Autowired EmpleoService empleo;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;
    @Autowired jakarta.persistence.EntityManager entities;
    int admin;
    String token;
    LocalDate hoy;
    static final String CLAVE="clave-gestion-nucleo";
    @BeforeEach void preparar() {
        admin=usuario("test-gestion-admin","ADMIN",null);token=auth.login(new AuthDtos.Login("test-gestion-admin",CLAVE)).accessToken();hoy=LocalDate.now(clock);
    }
    int usuario(String nombre,String rol,Integer empresa) {
        int id=jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,?) RETURNING id_usuario",Integer.class,nombre,passwords.encode(CLAVE));
        if(empresa==null) jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre=?",id,rol);
        else jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) SELECT ?,?,id_rol FROM Roles WHERE nombre=?",id,empresa,rol);
        return id;
    }
    Identidad fiscal(String nif) { return new Identidad("EMPRESA",null,null,"Sociedad privada",nif,"123456","privado@example.test"); }
    ClienteEntrada cliente(Long version,String codigo,String nif) { return new ClienteEntrada(version,codigo,fiscal(nif),"0",null,null,null); }
    ClienteFicha cliente() { return maestros.crearCliente(admin,1,cliente(null,"C-GESTION","NIF-GESTION")); }
    SerieFicha serie(TipoSerie tipo) { return obras.crearSerie(admin,1,tipo,new SerieEntrada("Gestion "+tipo,tipo==TipoSerie.OBRA?"OG":"PG",hoy.getYear(),4)); }
    ObraEntrada obraEntrada(Long version,int serie,int cliente) { return new ObraEntrada(version,serie,cliente,"Obra nueva","Descripcion privada","120.00",hoy,hoy.plusDays(30)); }
    PresupuestoEntrada presupuestoEntrada(Long version,int serie,int cliente,Integer obra) {
        return new PresupuestoEntrada(version,serie,cliente,obra,hoy,hoy.plusDays(30),List.of(new EntradaLinea("Material privado","3","0.05","10","21")));
    }
    void error(Runnable accion,int status) { assertThatThrownBy(accion::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(status)); }

    @Test void clienteCreadoConsultableEnAndroidYActualizablePorVersion() throws Exception {
        var c=cliente();
        mvc.perform(get("/api/v1/empresas/1/clientes/"+c.id()).header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("codigo").value("C-GESTION")).andExpect(jsonPath("version").doesNotExist());
        var mod=maestros.actualizarCliente(admin,1,c.id(),cliente(0L,"C-EDITADO","NIF-GESTION"));assertThat(mod.version()).isEqualTo(1);
        assertThat(maestros.cliente(admin,1,c.id()).datos().codigo()).isEqualTo("C-EDITADO");
        error(()->maestros.actualizarCliente(admin,1,c.id(),cliente(0L,"C-OBSOLETO","NIF-GESTION")),409);
    }
    @Test void bajaLogicaClienteConservaFichaYNoPermiteNuevasObras() {
        var c=cliente();var s=serie(TipoSerie.OBRA);var baja=maestros.actividadCliente(admin,1,c.id(),new Actividad(0L,false));assertThat(baja.activo()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Clientes WHERE id_cliente=?",Integer.class,c.id())).isEqualTo(1);
        error(()->obras.crearObra(admin,1,obraEntrada(null,s.id(),c.id())),409);
    }
    @Test void fichaCompartidaExigeEscrituraEnTodasLasEmpresas() {
        var c=cliente();compartir.compartirCliente(admin,1,c.id(),2);
        int local=usuario("test-gestion-local","ADMIN",1);
        error(()->maestros.actualizarCliente(local,1,c.id(),cliente(0L,"C-INDEBIDO","NIF-GESTION")),404);
    }
    @Test void actualizacionCompartidaPorAdminSeReflejaEnAmbasEmpresas() {
        var c=cliente();compartir.compartirCliente(admin,1,c.id(),2);maestros.actualizarCliente(admin,1,c.id(),cliente(0L,"C-COMPARTIDO","NIF-GESTION"));
        assertThat(maestros.cliente(admin,2,c.id()).datos().codigo()).isEqualTo("C-COMPARTIDO");
    }
    @Test void identidadUsadaPorOtraFichaNoSeModificaIndirectamente() {
        var c=cliente();int entidad=jdbc.queryForObject("SELECT id_entidad FROM Clientes WHERE id_cliente=?",Integer.class,c.id());
        jdbc.update("INSERT INTO Proveedores(id_entidad,codigo_proveedor,tipo_proveedor) VALUES (?,'P-OTRO-USO','EMPRESA')",entidad);
        error(()->maestros.actualizarCliente(admin,1,c.id(),cliente(0L,"C-OTRO","NIF-GESTION")),409);
    }
    @Test void nifDuplicadoNoExponeIdentidadExistente() { cliente();error(()->maestros.crearCliente(admin,1,cliente(null,"C-OTRO","NIF-GESTION")),409); }
    @Test void codigoDuplicadoRespetaCollation() { cliente();error(()->maestros.crearCliente(admin,1,cliente(null,"c-gestion","NIF-OTRO")),409); }
    @ParameterizedTest @ValueSource(strings={"-1","101","0.001","NaN"}) void descuentoInvalido(String descuento) {
        error(()->maestros.crearCliente(admin,1,new ClienteEntrada(null,"C-MAL",fiscal("NIF-MAL"),descuento,null,null,null)),400);
    }
    @Test void emailInvalidoEsErrorDeEntrada() {
        var f=new Identidad("EMPRESA",null,null,"Prueba","NIF-MAL",null,"invalido");
        error(()->maestros.crearCliente(admin,1,new ClienteEntrada(null,"C-MAL",f,"0",null,null,null)),400);
    }
    @ParameterizedTest @ValueSource(strings={"a..b@example.test",".persona@example.test","persona@-dominio.test"})
    void correoMalFormadoDevuelve400SinErrorDePersistencia(String email) throws Exception {
        var identidad=new Identidad("EMPRESA",null,null,"Prueba","NIF-EMAIL",null,email);
        var dato=new ClienteEntrada(null,"C-EMAIL",identidad,"0",null,null,null);
        mvc.perform(post("/api/v1/empresas/1/gestion/clientes").header("Authorization","Bearer "+token)
            .contentType("application/json").content(json.writeValueAsString(dato)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("campos[0]").value("email"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Clientes WHERE codigo_cliente='C-EMAIL'",Integer.class)).isZero();
    }
    @Test void proveedorNoLeeNiSobrescribeIban() throws Exception {
        var p=maestros.crearProveedor(admin,1,new ProveedorEntrada(null,"P-GESTION",fiscal("NIF-PROV"),"EMPRESA"));
        jdbc.update("UPDATE Proveedores SET iban='PRIVADO' WHERE id_proveedor=?",p.id());
        // La siguiente peticion HTTP abriria otro contexto JPA. Recargar el fixture
        // insertado por JDBC evita probar contra la copia anterior en memoria.
        entities.clear();
        var actual=maestros.actualizarProveedor(admin,1,p.id(),new ProveedorEntrada(0L,"P-NUEVO",fiscal("NIF-PROV"),"EMPRESA"));
        assertThat(json.writeValueAsString(actual)).doesNotContain("iban","PRIVADO");assertThat(jdbc.queryForObject("SELECT iban FROM Proveedores WHERE id_proveedor=?",String.class,p.id())).isEqualTo("PRIVADO");
        assertThat(maestros.actividadProveedor(admin,1,p.id(),new Actividad(1L,false)).activo()).isFalse();
    }
    @Test void ibanCompletoQuedaEnGestionFinancieraYLaConsultaSoloLoEnmascara() throws Exception {
        var c=cliente();
        var guardado=maestros.ibanCliente(admin,1,c.id(),new IbanEntrada(0L,IBAN_SINTETICO_FORMATADO));
        assertThat(guardado.iban()).isEqualTo(IBAN_SINTETICO);
        assertThat(maestros.ibanCliente(admin,1,c.id()).iban()).isEqualTo(IBAN_SINTETICO);
        mvc.perform(get("/api/v1/empresas/1/clientes/"+c.id()).header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("ibanEnmascarado").value("****5432"));
    }
    OperarioEntrada operario(Long version,LocalDate alta,LocalDate baja) { return new OperarioEntrada(version,"OP-GESTION",new Identidad("PERSONA","Persona","Privada",null,"NIF-OP",null,null),alta,baja,null); }
    @Test void empleadoCreadoYBajaSinControlHorarioNiDatosSalariales() throws Exception {
        var o=maestros.crearOperario(admin,1,operario(null,hoy.minusDays(10),null));
        var actual=maestros.actualizarOperario(admin,1,o.id(),operario(0L,hoy.minusDays(10),hoy));assertThat(actual.datos().baja()).isEqualTo(hoy);
        assertThat(json.writeValueAsString(actual)).doesNotContain("nss","costeHora","horasSemanales");
        assertThat(maestros.actividadOperario(admin,1,o.id(),new Actividad(1L,false)).activo()).isFalse();
    }
    @Test void empleadoAdmiteTodosLosDatosAdministrativosNuevosVacios() {
        var identidad=new Identidad("PERSONA","Sin documento",null,null,null,null,null,null,null);
        var o=maestros.crearOperario(admin,1,new OperarioEntrada(null,"OP-SIN-DATOS",identidad,hoy,null,null));
        var ficha=maestros.operario(admin,1,o.id());
        assertThat(ficha.datos().identidad().nif()).isNull();
        assertThat(ficha.datos().identidad().telefono()).isNull();
        assertThat(ficha.datos().identidad().email()).isNull();
        assertThat(maestros.ibanOperario(admin,1,o.id()).iban()).isNull();
    }
    @Test void ibanDeEmpleadoSoloSeGestionaPorSeparadoYAdmiteAusencia() throws Exception {
        var o=maestros.crearOperario(admin,1,operario(null,hoy,null));
        var iban=maestros.ibanOperario(admin,1,o.id(),new IbanEntrada(0L,IBAN_SINTETICO_FORMATADO));
        assertThat(iban.iban()).isEqualTo(IBAN_SINTETICO);
        assertThat(json.writeValueAsString(maestros.operario(admin,1,o.id()))).doesNotContain("GB05TEST","iban");
        assertThat(maestros.ibanOperario(admin,1,o.id(),new IbanEntrada(1L,null)).iban()).isNull();
    }
    @Test void cambioPeriodoLaboralNoDejaAsignacionesFuera() {
        var o=maestros.crearOperario(admin,1,operario(null,hoy.minusDays(10),null));empleo.asignar(admin,1,o.id(),new EmpleoDtos.NuevaAsignacion(1001,hoy,hoy.plusDays(3)));
        error(()->maestros.actualizarOperario(admin,1,o.id(),operario(0L,hoy.minusDays(10),hoy)),409);
    }
    @Test void obraNumeradaYEstadosConHistorialSinBorrar() {
        var c=cliente();var s=serie(TipoSerie.OBRA);var o=obras.crearObra(admin,1,obraEntrada(null,s.id(),c.id()));
        assertThat(o.codigo()).isEqualTo("OG1-"+hoy.getYear()+"-0001");assertThat(o.estado()).isEqualTo("PRESUPUESTO");
        o=obras.estadoObra(admin,1,o.id(),new CambioObra(0L,EstadoObra.EN_CURSO,hoy));
        o=obras.estadoObra(admin,1,o.id(),new CambioObra(1L,EstadoObra.FINALIZADA,hoy));assertThat(o.finReal()).isEqualTo(hoy);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Historial_Estados_Obra WHERE id_obra=?",Integer.class,o.id())).isEqualTo(3);
        int id=o.id();error(()->obras.actualizarObra(admin,1,id,obraEntrada(2L,s.id(),c.id())),409);
    }
    @Test void obraNoCambiaClienteNiSerieNiEmpresa() {
        var c=cliente();var s=serie(TipoSerie.OBRA);var o=obras.crearObra(admin,1,obraEntrada(null,s.id(),c.id()));
        error(()->obras.actualizarObra(admin,1,o.id(),obraEntrada(0L,s.id(),101)),409);
    }
    @Test void clienteCompartidoPermiteCrearObraSinCompartirLaObra() {
        var s=serie(TipoSerie.OBRA);var o=obras.crearObra(admin,1,obraEntrada(null,s.id(),102));
        assertThat(o.datos().clienteId()).isEqualTo(102);
        assertThat(jdbc.queryForObject("SELECT id_empresa FROM API_Obra_Empresa WHERE id_obra=?",Integer.class,o.id())).isEqualTo(1);
    }
    @Test void serieAjenaNoSeUtiliza() { var s=serie(TipoSerie.OBRA);error(()->obras.crearObra(admin,2,obraEntrada(null,s.id(),102)),404); }
    @Test void presupuestoCalculaPorLineaSustituyeSoloPendienteYSeConsultaEnContratoAnterior() throws Exception {
        var c=cliente();var s=serie(TipoSerie.PRESUPUESTO);var p=obras.crearPresupuesto(admin,1,presupuestoEntrada(null,s.id(),c.id(),null));
        assertThat(p.subtotal()).isEqualTo("0.15");assertThat(p.descuento()).isEqualTo("0.02");assertThat(p.impuestos()).isEqualTo("0.03");assertThat(p.total()).isEqualTo("0.16");
        mvc.perform(get("/api/v1/empresas/1/presupuestos/"+p.id()).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("total").value("0.16"));
        var edit=new PresupuestoEntrada(0L,s.id(),c.id(),null,hoy,null,List.of(new EntradaLinea("Cambio","2","10","0","21")));
        var cambiado=obras.actualizarPresupuesto(admin,1,p.id(),edit);assertThat(cambiado.total()).isEqualTo("24.20");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Lineas_Presupuesto WHERE id_presupuesto=?",Integer.class,p.id())).isEqualTo(1);
        assertThat(obras.estadoPresupuesto(admin,1,p.id(),new CambioPresupuesto(1L,EstadoPresupuesto.ACEPTADO)).estado()).isEqualTo("ACEPTADO");
        error(()->obras.actualizarPresupuesto(admin,1,p.id(),new PresupuestoEntrada(2L,s.id(),c.id(),null,hoy,null,edit.lineas())),409);
    }
    @Test void presupuestoHistoricoNoSeRecalculaNiSeEdita() {
        int id=jdbc.queryForObject("SELECT p.id_presupuesto FROM Presupuestos p JOIN API_Presupuesto_Empresa e ON e.id_presupuesto=p.id_presupuesto WHERE e.id_empresa=1 ORDER BY p.id_presupuesto LIMIT 1",Integer.class);
        var p=obras.presupuesto(admin,1,id);assertThat(p.gestionado()).isFalse();
        error(()->obras.actualizarPresupuesto(admin,1,id,p.datos()),409);
    }
    @Test void presupuestoConObraDeOtroClienteSeRechaza() {
        var c=cliente();var s=serie(TipoSerie.PRESUPUESTO);error(()->obras.crearPresupuesto(admin,1,presupuestoEntrada(null,s.id(),c.id(),1001)),404);
    }
    @Test void fechasYLineasInvalidasNoSeAceptan() {
        var c=cliente();var s=serie(TipoSerie.PRESUPUESTO);
        error(()->obras.crearPresupuesto(admin,1,new PresupuestoEntrada(null,s.id(),c.id(),null,hoy,hoy.minusDays(1),List.of(new EntradaLinea("X","1","1","0","21")))),400);
    }
    @Test void auditoriaNoGuardaIdentidadNiTextosPeroIncluyeVersionAnterior() {
        var c=cliente();maestros.actualizarCliente(admin,1,c.id(),cliente(0L,"C-CAMBIADO","NIF-GESTION"));
        var cambios=jdbc.queryForObject("SELECT cambios::text FROM API_Auditoria WHERE modulo='GESTION' AND entidad='CLIENTE' AND entidad_id=? AND accion='ACTUALIZAR'",String.class,c.id());
        assertThat(cambios).doesNotContain("NIF","Sociedad","privado@example","C-CAMBIADO");
        assertThat(json.readTree(cambios).at("/antes/version").asLong()).isZero();assertThat(json.readTree(cambios).at("/despues/version").asLong()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"clientes","proveedores","operarios","obras","presupuestos"})
    void lectorNoAccedeAGestionNiEscribe(String recurso) throws Exception {
        usuario("test-gestion-lector","LECTURA_EMPRESA",1);String lector=auth.login(new AuthDtos.Login("test-gestion-lector",CLAVE)).accessToken();
        mvc.perform(get("/api/v1/empresas/1/gestion/"+recurso+"/101").header("Authorization","Bearer "+lector)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/empresas/1/gestion/"+recurso).header("Authorization","Bearer "+lector).contentType("application/json").content("{}")).andExpect(status().isNotFound());
    }
    @Test void rutasHttpCreanActualizanYRechazanVersionAusente() throws Exception {
        var response=mvc.perform(post("/api/v1/empresas/1/gestion/clientes").header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(cliente(null,"C-HTTP","NIF-HTTP"))))
            .andExpect(status().isCreated()).andReturn().getResponse();int id=json.readTree(response.getContentAsString()).path("id").asInt();
        mvc.perform(get("/api/v1/empresas/1/gestion/clientes/"+id).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("version").value(0));
        mvc.perform(put("/api/v1/empresas/1/gestion/clientes/"+id).header("Authorization","Bearer "+token).contentType("application/json").content(json.writeValueAsString(cliente(null,"C-HTTP","NIF-HTTP"))))
            .andExpect(status().isBadRequest());
    }
    @Test void seriesPaginadasYFiltrosEstricos() {
        serie(TipoSerie.OBRA);var q=new LinkedMultiValueMap<String,String>();q.add("size","1");
        assertThat(obras.series(admin,1,TipoSerie.OBRA,q).items()).hasSize(1);q.add("size","2");error(()->obras.series(admin,1,TipoSerie.OBRA,q),400);
    }
}
