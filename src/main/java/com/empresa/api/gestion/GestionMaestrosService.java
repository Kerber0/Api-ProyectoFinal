package com.empresa.api.gestion;

import com.empresa.api.common.ApiException;
import com.empresa.api.model.*;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.empresa.api.gestion.GestionDtos.*;
import static com.empresa.api.facturacion.CalculoFactura.texto;

@Service @Transactional(readOnly=true)
public class GestionMaestrosService {
    private final GestionRepository r;
    private final AutorizacionService acceso;
    private final Clock clock;
    private final jakarta.validation.Validator validator;
    public GestionMaestrosService(GestionRepository r,AutorizacionService acceso,Clock clock,jakarta.validation.Validator validator) {
        this.r=r;this.acceso=acceso;this.clock=clock;this.validator=validator;
    }
    private void permiso(int autor,int empresa,String permiso) {
        acceso.exigirLectura(autor,empresa,"EMPRESA"); acceso.exigirEscritura(autor,empresa,permiso);
    }
    private void comenzar(int autor,int empresa,String permiso) { permiso(autor,empresa,permiso);r.serializar();permiso(autor,empresa,permiso); }
    private void compartido(int autor,Set<Empresa> empresas,String permiso) { for(var e:empresas) permiso(autor,e.getId(),permiso); }
    private LocalDateTime ahora() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
    static void version(Long esperada,long actual) {
        if(esperada==null||esperada<0) throw ApiException.invalido("version");
        if(esperada!=actual) throw ApiException.conflicto("La ficha ha cambiado; volver a consultarla");
    }
    static void nueva(Long version) { if(version!=null) throw ApiException.invalido("version"); }
    static String opcional(String valor,int max,String campo) { return valor==null?null:texto(valor,max,campo); }
    static BigDecimal decimal(String valor,int enteros,String campo) {
        if(valor==null||!valor.matches("[0-9]{1,"+enteros+"}(\\.[0-9]{1,2})?")) throw ApiException.invalido(campo);
        return new BigDecimal(valor).setScale(2);
    }
    static void fecha(LocalDate f,String campo) { if(f==null||f.getYear()<2000||f.getYear()>9999) throw ApiException.invalido(campo); }
    static void rango(LocalDate desde,LocalDate hasta) {
        if(desde!=null) fecha(desde,"desde"); if(hasta!=null) fecha(hasta,"hasta");
        if(desde!=null&&hasta!=null&&hasta.isBefore(desde)) throw ApiException.invalido("hasta");
    }
    private Identidad identidad(Entidad e) { return new Identidad(e.getTipoEntidad(),e.getNombre(),e.getApellidos(),e.getRazonSocial(),e.getNifCif(),e.getTelefonoFijo(),e.getMovil(),e.getEmail(),e.getObservaciones()); }
    private Entidad identidad(int autor,Identidad dato,Entidad e) { return identidad(autor,dato,e,true); }
    private Entidad identidad(int autor,Identidad dato,Entidad e,boolean nifObligatorio) {
        if(dato==null||!Set.of("PERSONA","EMPRESA").contains(dato.tipo()==null?"":dato.tipo())) throw ApiException.invalido("identidad");
        if(e==null) {
            e=new Entidad();e.setActivo(true);e.setFechaAlta(LocalDate.now(clock));e.setFechaCreacion(ahora());e.setUsuarioCreacion(r.referencia(Usuario.class,autor));
        } else {
            r.identidadExclusiva(e);
            if(!e.getTipoEntidad().equals(dato.tipo())) throw ApiException.conflicto("El tipo de identidad no se cambia");
        }
        if(dato.tipo().equals("PERSONA")) {
            if(dato.razonSocial()!=null) throw ApiException.invalido("razonSocial");
            e.setNombre(texto(dato.nombre(),100,"nombre"));e.setApellidos(opcional(dato.apellidos(),150,"apellidos"));
        } else {
            if(dato.nombre()!=null||dato.apellidos()!=null) throw ApiException.invalido("nombre");
            e.setRazonSocial(texto(dato.razonSocial(),150,"razonSocial"));
        }
        String nif=nifObligatorio?texto(dato.nif(),20,"nif"):opcional(dato.nif(),20,"nif");r.nifLibre(nif,e.getId());e.setNifCif(nif);e.setTipoEntidad(dato.tipo());
        e.setTelefonoFijo(opcional(dato.telefono(),20,"telefono"));
        e.setMovil(opcional(dato.movil(),20,"movil"));
        String email=opcional(dato.email(),150,"email");
        if(email!=null&&(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                ||!validator.validateValue(Entidad.class,"email",email).isEmpty())) throw ApiException.invalido("email");
        e.setEmail(email);e.setObservaciones(opcional(dato.observaciones(),2000,"observaciones"));e.setFechaModificacion(ahora());e.setUsuarioModificacion(r.referencia(Usuario.class,autor));
        if(e.getId()==null) r.guardar(e);return e;
    }
    private static Map<String,?> estado(long version,Entidad e) { return Map.of("version",version,"activo",e.getActivo()); }
    public ClienteFicha cliente(int autor,int empresa,int id) {
        permiso(autor,empresa,"CLIENTES");var c=r.cliente(empresa,id);compartido(autor,c.getEmpresasVisibles(),"CLIENTES");return ficha(c);
    }
    private ClienteFicha ficha(Cliente c) {
        return new ClienteFicha(c.getId(),c.getVersionGestion(),new ClienteEntrada(c.getVersionGestion(),c.getCodigoCliente(),identidad(c.getEntidad()),c.getDescuento().toPlainString(),
            c.getLimiteCredito()==null?null:c.getLimiteCredito().toPlainString(),c.getZona()==null?null:c.getZona().getId(),c.getFormaPago()==null?null:c.getFormaPago().getId()),c.getEntidad().getActivo());
    }
    @Transactional public ClienteFicha crearCliente(int autor,int empresa,ClienteEntrada dato) {
        comenzar(autor,empresa,"CLIENTES");nueva(dato.version());var visibles=r.empresasActivas();
        compartido(autor,visibles,"CLIENTES");var c=new Cliente();
        c.getEmpresasVisibles().addAll(visibles);c.setFechaCreacion(ahora());c.setUsuarioCreacion(r.referencia(Usuario.class,autor));
        aplicar(autor,c,dato);r.guardar(c);r.auditar(autor,empresa,"CLIENTE",c.getId(),"CREAR",null,estado(0,c.getEntidad()));return ficha(c);
    }
    @Transactional public ClienteFicha actualizarCliente(int autor,int empresa,int id,ClienteEntrada dato) {
        comenzar(autor,empresa,"CLIENTES");var c=r.cliente(empresa,id);compartido(autor,c.getEmpresasVisibles(),"CLIENTES");version(dato.version(),c.getVersionGestion());
        var antes=estado(c.getVersionGestion(),c.getEntidad());aplicar(autor,c,dato);c.setVersionGestion(c.getVersionGestion()+1);
        r.auditar(autor,empresa,"CLIENTE",id,"ACTUALIZAR",antes,estado(c.getVersionGestion(),c.getEntidad()));return ficha(c);
    }
    private void aplicar(int autor,Cliente c,ClienteEntrada dato) {
        String codigo=texto(dato.codigo(),20,"codigo");r.codigoLibre("CLIENTE",codigo,c.getId());
        var descuento=decimal(dato.descuento(),3,"descuento");if(descuento.compareTo(new BigDecimal("100"))>0) throw ApiException.invalido("descuento");
        c.setCodigoCliente(codigo);c.setDescuento(descuento);c.setLimiteCredito(dato.limiteCredito()==null?null:decimal(dato.limiteCredito(),10,"limiteCredito"));
        var zona=dato.zonaId()==null?null:r.referencia(Zona.class,dato.zonaId());if(zona!=null&&!zona.getActivo()) throw ApiException.noEncontrado();c.setZona(zona);
        var forma=dato.formaPagoId()==null?null:r.referencia(FormaPago.class,dato.formaPagoId());if(forma!=null&&!forma.getActivo()) throw ApiException.noEncontrado();c.setFormaPago(forma);
        c.setEntidad(identidad(autor,dato.identidad(),c.getEntidad()));c.setFechaModificacion(ahora());c.setUsuarioModificacion(r.referencia(Usuario.class,autor));
    }
    @Transactional public ClienteFicha actividadCliente(int autor,int empresa,int id,Actividad dato) {
        comenzar(autor,empresa,"CLIENTES");var c=r.cliente(empresa,id);compartido(autor,c.getEmpresasVisibles(),"CLIENTES");version(dato.version(),c.getVersionGestion());
        var antes=estado(c.getVersionGestion(),c.getEntidad());actividad(autor,c.getEntidad(),dato);c.setVersionGestion(c.getVersionGestion()+1);c.setFechaModificacion(ahora());c.setUsuarioModificacion(r.referencia(Usuario.class,autor));
        r.auditar(autor,empresa,"CLIENTE",id,"ACTIVIDAD",antes,estado(c.getVersionGestion(),c.getEntidad()));return ficha(c);
    }
    private void actividad(int autor,Entidad e,Actividad dato) {
        if(dato.activo()==null) throw ApiException.invalido("activo");r.identidadExclusiva(e);e.setActivo(dato.activo());e.setFechaModificacion(ahora());e.setUsuarioModificacion(r.referencia(Usuario.class,autor));
    }
    public ProveedorFicha proveedor(int autor,int empresa,int id) {
        permiso(autor,empresa,"PROVEEDORES");var p=r.proveedor(empresa,id);compartido(autor,p.getEmpresasVisibles(),"PROVEEDORES");return ficha(p);
    }
    private ProveedorFicha ficha(Proveedor p) { return new ProveedorFicha(p.getId(),p.getVersionGestion(),new ProveedorEntrada(p.getVersionGestion(),p.getCodigoProveedor(),identidad(p.getEntidad()),p.getTipoProveedor()),p.getEntidad().getActivo()); }
    @Transactional public ProveedorFicha crearProveedor(int autor,int empresa,ProveedorEntrada dato) {
        comenzar(autor,empresa,"PROVEEDORES");nueva(dato.version());var visibles=r.empresasActivas();
        compartido(autor,visibles,"PROVEEDORES");var p=new Proveedor();p.getEmpresasVisibles().addAll(visibles);
        p.setFechaCreacion(ahora());p.setUsuarioCreacion(r.referencia(Usuario.class,autor));aplicar(autor,p,dato);r.guardar(p);
        r.auditar(autor,empresa,"PROVEEDOR",p.getId(),"CREAR",null,estado(0,p.getEntidad()));return ficha(p);
    }
    @Transactional public ProveedorFicha actualizarProveedor(int autor,int empresa,int id,ProveedorEntrada dato) {
        comenzar(autor,empresa,"PROVEEDORES");var p=r.proveedor(empresa,id);compartido(autor,p.getEmpresasVisibles(),"PROVEEDORES");version(dato.version(),p.getVersionGestion());
        var antes=estado(p.getVersionGestion(),p.getEntidad());aplicar(autor,p,dato);p.setVersionGestion(p.getVersionGestion()+1);
        r.auditar(autor,empresa,"PROVEEDOR",id,"ACTUALIZAR",antes,estado(p.getVersionGestion(),p.getEntidad()));return ficha(p);
    }
    private void aplicar(int autor,Proveedor p,ProveedorEntrada dato) {
        if(dato.tipo()==null||!Set.of("EMPRESA","AUTONOMO").contains(dato.tipo())) throw ApiException.invalido("tipo");
        String codigo=texto(dato.codigo(),20,"codigo");r.codigoLibre("PROVEEDOR",codigo,p.getId());p.setCodigoProveedor(codigo);p.setTipoProveedor(texto(dato.tipo(),20,"tipo"));
        p.setEntidad(identidad(autor,dato.identidad(),p.getEntidad()));p.setFechaModificacion(ahora());p.setUsuarioModificacion(r.referencia(Usuario.class,autor));
    }
    @Transactional public ProveedorFicha actividadProveedor(int autor,int empresa,int id,Actividad dato) {
        comenzar(autor,empresa,"PROVEEDORES");var p=r.proveedor(empresa,id);compartido(autor,p.getEmpresasVisibles(),"PROVEEDORES");version(dato.version(),p.getVersionGestion());
        var antes=estado(p.getVersionGestion(),p.getEntidad());actividad(autor,p.getEntidad(),dato);p.setVersionGestion(p.getVersionGestion()+1);p.setFechaModificacion(ahora());p.setUsuarioModificacion(r.referencia(Usuario.class,autor));
        r.auditar(autor,empresa,"PROVEEDOR",id,"ACTIVIDAD",antes,estado(p.getVersionGestion(),p.getEntidad()));return ficha(p);
    }
    private void financiero(int autor,Set<Empresa> empresas,String maestro,String permisoFinanciero) {
        for(var e:empresas){acceso.exigirEscritura(autor,e.getId(),maestro);acceso.exigirEscritura(autor,e.getId(),permisoFinanciero);}
    }
    static String iban(String valor) {
        if(valor==null)return null; String x=valor.replace(" ","").toUpperCase(Locale.ROOT);
        if(!x.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}"))throw ApiException.invalido("iban");
        String moved=x.substring(4)+x.substring(0,4);int mod=0;
        for(char c:moved.toCharArray())for(char d:(c>='A'?Integer.toString(c-'A'+10):Character.toString(c)).toCharArray())mod=(mod*10+d-'0')%97;
        if(mod!=1)throw ApiException.invalido("iban"); return x;
    }
    public IbanFicha ibanCliente(int autor,int empresa,int id){var c=r.cliente(empresa,id);financiero(autor,c.getEmpresasVisibles(),"CLIENTES","COBROS");return new IbanFicha(c.getVersionGestion(),c.getIban());}
    public IbanFicha ibanProveedor(int autor,int empresa,int id){var p=r.proveedor(empresa,id);financiero(autor,p.getEmpresasVisibles(),"PROVEEDORES","PAGOS");return new IbanFicha(p.getVersionGestion(),p.getIban());}
    public IbanFicha ibanOperario(int autor,int empresa,int id){permiso(autor,empresa,"OPERARIOS");var o=r.operario(empresa,id);return new IbanFicha(o.getVersionGestion(),o.getIban());}
    @Transactional public IbanFicha ibanCliente(int autor,int empresa,int id,IbanEntrada dato){r.serializar();var c=r.cliente(empresa,id);financiero(autor,c.getEmpresasVisibles(),"CLIENTES","COBROS");version(dato.version(),c.getVersionGestion());boolean antes=c.getIban()!=null;c.setIban(iban(dato.iban()));c.setVersionGestion(c.getVersionGestion()+1);c.setFechaModificacion(ahora());c.setUsuarioModificacion(r.referencia(Usuario.class,autor));r.auditar(autor,empresa,"CLIENTE",id,"IBAN",Map.of("informado",antes),Map.of("informado",c.getIban()!=null));return new IbanFicha(c.getVersionGestion(),c.getIban());}
    @Transactional public IbanFicha ibanProveedor(int autor,int empresa,int id,IbanEntrada dato){r.serializar();var p=r.proveedor(empresa,id);financiero(autor,p.getEmpresasVisibles(),"PROVEEDORES","PAGOS");version(dato.version(),p.getVersionGestion());boolean antes=p.getIban()!=null;p.setIban(iban(dato.iban()));p.setVersionGestion(p.getVersionGestion()+1);p.setFechaModificacion(ahora());p.setUsuarioModificacion(r.referencia(Usuario.class,autor));r.auditar(autor,empresa,"PROVEEDOR",id,"IBAN",Map.of("informado",antes),Map.of("informado",p.getIban()!=null));return new IbanFicha(p.getVersionGestion(),p.getIban());}
    @Transactional public IbanFicha ibanOperario(int autor,int empresa,int id,IbanEntrada dato){r.serializar();permiso(autor,empresa,"OPERARIOS");var o=r.operario(empresa,id);version(dato.version(),o.getVersionGestion());boolean antes=o.getIban()!=null;o.setIban(iban(dato.iban()));o.setVersionGestion(o.getVersionGestion()+1);o.setFechaModificacion(ahora());o.setUsuarioModificacion(r.referencia(Usuario.class,autor));r.auditar(autor,empresa,"OPERARIO",id,"IBAN",Map.of("informado",antes),Map.of("informado",o.getIban()!=null));return new IbanFicha(o.getVersionGestion(),o.getIban());}
    public OperarioFicha operario(int autor,int empresa,int id) { permiso(autor,empresa,"OPERARIOS");return ficha(r.operario(empresa,id)); }
    private OperarioFicha ficha(Operario o) { return new OperarioFicha(o.getId(),o.getVersionGestion(),new OperarioEntrada(o.getVersionGestion(),o.getCodigoOperario(),identidad(o.getEntidad()),o.getFechaAlta(),o.getFechaBaja(),o.getCategoria()==null?null:o.getCategoria().getId()),o.getEntidad().getActivo()); }
    @Transactional public OperarioFicha crearOperario(int autor,int empresa,OperarioEntrada dato) {
        comenzar(autor,empresa,"OPERARIOS");nueva(dato.version());var o=new Operario();o.setEmpresa(r.referencia(Empresa.class,empresa));o.setFechaCreacion(ahora());o.setUsuarioCreacion(r.referencia(Usuario.class,autor));
        aplicar(autor,o,dato);r.guardar(o);r.auditar(autor,empresa,"OPERARIO",o.getId(),"CREAR",null,estado(0,o.getEntidad()));return ficha(o);
    }
    @Transactional public OperarioFicha actualizarOperario(int autor,int empresa,int id,OperarioEntrada dato) {
        comenzar(autor,empresa,"OPERARIOS");var o=r.operario(empresa,id);version(dato.version(),o.getVersionGestion());var antes=estado(o.getVersionGestion(),o.getEntidad());
        aplicar(autor,o,dato);o.setVersionGestion(o.getVersionGestion()+1);r.auditar(autor,empresa,"OPERARIO",id,"ACTUALIZAR",antes,estado(o.getVersionGestion(),o.getEntidad()));return ficha(o);
    }
    private void aplicar(int autor,Operario o,OperarioEntrada dato) {
        if(dato.identidad()==null||!"PERSONA".equals(dato.identidad().tipo())) throw ApiException.invalido("identidad");fecha(dato.alta(),"alta");rango(dato.alta(),dato.baja());
        if(o.getId()!=null) r.periodoOperario(o.getId(),dato.alta(),dato.baja());
        String codigo=texto(dato.codigo(),20,"codigo");r.codigoLibre("OPERARIO",codigo,o.getId());o.setCodigoOperario(codigo);o.setFechaAlta(dato.alta());o.setFechaBaja(dato.baja());
        var categoria=dato.categoriaId()==null?null:r.referencia(CategoriaOperario.class,dato.categoriaId());if(categoria!=null&&!categoria.getActivo()) throw ApiException.noEncontrado();o.setCategoria(categoria);
        o.setEntidad(identidad(autor,dato.identidad(),o.getEntidad(),false));o.setFechaModificacion(ahora());o.setUsuarioModificacion(r.referencia(Usuario.class,autor));
    }
    @Transactional public OperarioFicha actividadOperario(int autor,int empresa,int id,Actividad dato) {
        comenzar(autor,empresa,"OPERARIOS");var o=r.operario(empresa,id);version(dato.version(),o.getVersionGestion());var antes=estado(o.getVersionGestion(),o.getEntidad());
        actividad(autor,o.getEntidad(),dato);o.setVersionGestion(o.getVersionGestion()+1);o.setFechaModificacion(ahora());o.setUsuarioModificacion(r.referencia(Usuario.class,autor));
        r.auditar(autor,empresa,"OPERARIO",id,"ACTIVIDAD",antes,estado(o.getVersionGestion(),o.getEntidad()));return ficha(o);
    }
}
