package com.empresa.api.gestion;

import com.empresa.api.common.*;
import com.empresa.api.model.*;
import com.empresa.api.security.AutorizacionService;
import com.empresa.api.facturacion.CalculoFactura;
import com.empresa.api.facturacion.FacturacionDtos.EntradaLinea;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.empresa.api.gestion.GestionDtos.*;
import static com.empresa.api.gestion.GestionMaestrosService.*;
import static com.empresa.api.facturacion.CalculoFactura.texto;

@Service @Transactional(readOnly=true)
public class GestionObrasService {
    private final GestionRepository r;
    private final AutorizacionService acceso;
    private final Clock clock;
    public GestionObrasService(GestionRepository r,AutorizacionService acceso,Clock clock) { this.r=r;this.acceso=acceso;this.clock=clock; }
    private void permiso(int autor,int empresa,String permiso) { acceso.exigirLectura(autor,empresa,"EMPRESA");acceso.exigirEscritura(autor,empresa,permiso); }
    private void comenzar(int autor,int empresa,String permiso) { permiso(autor,empresa,permiso);r.serializar();permiso(autor,empresa,permiso); }
    private LocalDateTime ahora() { return LocalDateTime.ofInstant(clock.instant(),ZoneOffset.UTC); }
    private Usuario autor(int id) { return r.referencia(Usuario.class,id); }
    private static int id(Integer id,String campo) { if(id==null||id<1) throw ApiException.invalido(campo);return id; }
    private String permiso(TipoSerie tipo) { return tipo==TipoSerie.OBRA?"OBRAS":"PRESUPUESTOS"; }
    public Pagina<SerieFicha> series(int autor,int empresa,TipoSerie tipo,MultiValueMap<String,String> q) {
        permiso(autor,empresa,permiso(tipo));int page=0,size=20;
        for(var e:q.entrySet()) {
            if(!Set.of("page","size").contains(e.getKey())||e.getValue().size()!=1) throw ApiException.invalido("query");
            try {int n=Integer.parseInt(e.getValue().getFirst());if(e.getKey().equals("page")) page=n;else size=n;}
            catch(NumberFormatException ex){throw ApiException.invalido(e.getKey());}
        }
        if(page<0||page>100000||size<1||size>100) throw ApiException.invalido("paginacion");return r.series(empresa,tipo,page,size);
    }
    @Transactional public SerieFicha crearSerie(int usuario,int empresa,TipoSerie tipo,SerieEntrada dato) {
        comenzar(usuario,empresa,permiso(tipo));String nombre=texto(dato.nombre(),100,"nombre"),prefijo=texto(dato.prefijo(),3,"prefijo");
        if(!prefijo.matches("[A-Z]{1,3}")||dato.ejercicio()==null||dato.ejercicio()<2000||dato.ejercicio()>9999||dato.longitud()==null||dato.longitud()<1||dato.longitud()>6) throw ApiException.invalido("serie");
        if(tipo==TipoSerie.OBRA && (prefijo+empresa+"-"+dato.ejercicio()+"-").length()+dato.longitud()>20) throw ApiException.invalido("longitud");
        if(r.serieDuplicada(empresa,tipo,nombre,prefijo,dato.ejercicio())) throw ApiException.conflicto("Nombre o prefijo de serie ya utilizado");
        var s=new SerieDocumento();s.setEmpresa(empresa);s.setTipoDocumento(tipo.name());s.setNombre(nombre);s.setPrefijo(prefijo);s.setEjercicio(dato.ejercicio());s.setLongitud(dato.longitud());
        s.setSiguienteNumero(1);s.setActivo(true);s.setFechaCreacion(ahora());s.setFechaModificacion(ahora());s.setUsuarioCreacion(autor(usuario));s.setUsuarioModificacion(autor(usuario));r.guardar(s);
        r.auditar(usuario,empresa,"SERIE",s.getId(),"CREAR",null,Map.of("tipo",tipo.name(),"ejercicio",s.getEjercicio()));return new SerieFicha(s.getId(),tipo,nombre,prefijo,s.getEjercicio(),s.getLongitud(),1);
    }
    private String numerar(SerieDocumento s,LocalDate fecha,boolean obra) {
        fecha(fecha,"fecha");if(fecha.getYear()!=s.getEjercicio()) throw ApiException.invalido("fecha");
        int n=s.getSiguienteNumero();if(n>=Math.pow(10,s.getLongitud())) throw ApiException.conflicto("Serie agotada");
        String numero=s.getPrefijo()+(obra?s.getEmpresa()+"-"+s.getEjercicio()+"-":"")+String.format(Locale.ROOT,"%0"+s.getLongitud()+"d",n);
        if(numero.length()>(obra?20:30)) throw ApiException.invalido("serie");
        s.setSiguienteNumero(n+1);s.setFechaModificacion(ahora());return numero;
    }
    private Cliente cliente(int usuario,int empresa,int id) {
        acceso.exigirLectura(usuario,empresa,"CLIENTES");var c=r.cliente(empresa,id);if(!c.getEntidad().getActivo()) throw ApiException.conflicto("Cliente inactivo");return c;
    }
    public ObraFicha obra(int usuario,int empresa,int id) { permiso(usuario,empresa,"OBRAS");return ficha(r.obra(empresa,id)); }
    private ObraFicha ficha(Obra o) { return new ObraFicha(o.getId(),o.getVersionGestion(),o.getCodigoObra(),o.getEstado().getDescripcion(),o.getFechaFinReal(),new ObraEntrada(o.getVersionGestion(),o.getSerie().getId(),o.getCliente().getId(),o.getTitulo(),o.getDescripcion(),o.getPresupuesto().toPlainString(),o.getFechaInicio(),o.getFechaFinPrevista())); }
    private Map<String,?> estado(Obra o) { return Map.of("version",o.getVersionGestion(),"estado",o.getEstado().getDescripcion()); }
    @Transactional public ObraFicha crearObra(int usuario,int empresa,ObraEntrada dato) {
        comenzar(usuario,empresa,"OBRAS");nueva(dato.version());var s=r.serie(empresa,TipoSerie.OBRA,id(dato.serieId(),"serieId"));
        var o=new Obra();o.setEmpresa(r.referencia(Empresa.class,empresa));o.setSerie(s);o.setCliente(cliente(usuario,empresa,id(dato.clienteId(),"clienteId")));o.setEstado(r.estadoObra("PRESUPUESTO"));
        aplicar(o,dato);o.setCodigoObra(numerar(s,LocalDate.of(s.getEjercicio(),1,1),true));o.setFechaCreacion(ahora());o.setFechaModificacion(ahora());o.setUsuarioCreacion(autor(usuario));o.setUsuarioModificacion(autor(usuario));r.guardar(o);
        historial(usuario,o,null);r.auditar(usuario,empresa,"OBRA",o.getId(),"CREAR",null,estado(o));return ficha(o);
    }
    @Transactional public ObraFicha actualizarObra(int usuario,int empresa,int id,ObraEntrada dato) {
        comenzar(usuario,empresa,"OBRAS");var o=r.obra(empresa,id);version(dato.version(),o.getVersionGestion());
        if(!Set.of("PRESUPUESTO","EN_CURSO").contains(o.getEstado().getDescripcion())) throw ApiException.conflicto("La obra esta cerrada");
        if(!Objects.equals(dato.serieId(),o.getSerie().getId())||!Objects.equals(dato.clienteId(),o.getCliente().getId())) throw ApiException.conflicto("Serie y cliente son inmutables");
        if(o.getEstado().getDescripcion().equals("EN_CURSO")&&!Objects.equals(dato.inicio(),o.getFechaInicio())) throw ApiException.conflicto("La fecha de inicio de una obra en curso no se cambia");
        var antes=estado(o);aplicar(o,dato);o.setVersionGestion(o.getVersionGestion()+1);o.setFechaModificacion(ahora());o.setUsuarioModificacion(autor(usuario));
        r.auditar(usuario,empresa,"OBRA",id,"ACTUALIZAR",antes,estado(o));return ficha(o);
    }
    private void aplicar(Obra o,ObraEntrada dato) {
        rango(dato.inicio(),dato.finPrevista());o.setTitulo(texto(dato.titulo(),200,"titulo"));o.setDescripcion(opcional(dato.descripcion(),4000,"descripcion"));
        o.setPresupuesto(decimal(dato.presupuesto(),10,"presupuesto"));o.setFechaInicio(dato.inicio());o.setFechaFinPrevista(dato.finPrevista());
    }
    @Transactional public ObraFicha estadoObra(int usuario,int empresa,int id,CambioObra dato) {
        comenzar(usuario,empresa,"OBRAS");var o=r.obra(empresa,id);version(dato.version(),o.getVersionGestion());
        if(dato.estado()==null) throw ApiException.invalido("estado");String actual=o.getEstado().getDescripcion(),nuevo=dato.estado().name();
        boolean valido=actual.equals("PRESUPUESTO")&&Set.of("EN_CURSO","CANCELADA").contains(nuevo)||actual.equals("EN_CURSO")&&Set.of("FINALIZADA","CANCELADA").contains(nuevo);
        if(!valido) throw ApiException.conflicto("Transicion de obra no permitida");
        fecha(dato.fecha(),"fecha");if(dato.fecha().isAfter(LocalDate.now(clock))) throw ApiException.invalido("fecha");
        if(o.getFechaInicio()!=null&&dato.fecha().isBefore(o.getFechaInicio())) throw ApiException.invalido("fecha");
        var antes=estado(o);var anterior=o.getEstado();o.setEstado(r.estadoObra(nuevo));
        if(nuevo.equals("EN_CURSO")) o.setFechaInicio(dato.fecha());else o.setFechaFinReal(dato.fecha());
        rango(o.getFechaInicio(),o.getFechaFinPrevista());
        o.setVersionGestion(o.getVersionGestion()+1);o.setFechaModificacion(ahora());o.setUsuarioModificacion(autor(usuario));historial(usuario,o,anterior);
        r.auditar(usuario,empresa,"OBRA",id,"ESTADO",antes,estado(o));return ficha(o);
    }
    private void historial(int usuario,Obra o,com.empresa.api.model.EstadoObra anterior) {
        var h=new HistorialEstadoObra();h.setObra(o);h.setEstadoAnterior(anterior);h.setEstadoNuevo(o.getEstado());h.setUsuario(autor(usuario));h.setFecha(ahora());h.setFechaCreacion(ahora());h.setFechaModificacion(ahora());h.setUsuarioCreacion(autor(usuario));h.setUsuarioModificacion(autor(usuario));r.guardar(h);
    }
    public PresupuestoFicha presupuesto(int usuario,int empresa,int id) { permiso(usuario,empresa,"PRESUPUESTOS");return ficha(r.presupuesto(empresa,id)); }
    private PresupuestoFicha ficha(Presupuesto p) {
        var lineas=p.getLineas().stream().sorted(Comparator.comparing(LineaPresupuesto::getId)).map(l->new EntradaLinea(l.getDescripcion(),l.getCantidad().toPlainString(),l.getPrecio().toPlainString(),l.getDescuento().toPlainString(),l.getIva().toPlainString())).toList();
        return new PresupuestoFicha(p.getId(),p.getVersionGestion(),p.getNumero(),p.getEstado().getDescripcion(),p.isGestionado(),p.getSubtotal().toPlainString(),p.getDescuento().toPlainString(),p.getImpuestos().toPlainString(),p.getTotal().toPlainString(),
            new PresupuestoEntrada(p.getVersionGestion(),p.getSerie().getId(),p.getCliente().getId(),p.getObra()==null?null:p.getObra().getId(),p.getFecha(),p.getValidez(),lineas));
    }
    private Map<String,?> estado(Presupuesto p) { return Map.of("version",p.getVersionGestion(),"estado",p.getEstado().getDescripcion(),"total",p.getTotal().toPlainString()); }
    @Transactional public PresupuestoFicha crearPresupuesto(int usuario,int empresa,PresupuestoEntrada dato) {
        comenzar(usuario,empresa,"PRESUPUESTOS");nueva(dato.version());var s=r.serie(empresa,TipoSerie.PRESUPUESTO,id(dato.serieId(),"serieId"));var p=new Presupuesto();
        p.setEmpresa(r.referencia(Empresa.class,empresa));p.setSerie(s);p.setCliente(cliente(usuario,empresa,id(dato.clienteId(),"clienteId")));p.setEstado(r.estadoPresupuesto("PENDIENTE"));p.setGestionado(true);
        if(dato.obraId()!=null) {
            acceso.exigirLectura(usuario,empresa,"OBRAS");var o=r.obra(empresa,dato.obraId());
            if(!o.getCliente().getId().equals(p.getCliente().getId())) throw ApiException.noEncontrado();
            if(!Set.of("PRESUPUESTO","EN_CURSO").contains(o.getEstado().getDescripcion())) throw ApiException.conflicto("Obra cerrada");p.setObra(o);
        }
        p.setLineas(new ArrayList<>());p.setFechaCreacion(ahora());p.setUsuarioCreacion(autor(usuario));aplicar(usuario,p,dato);p.setNumero(numerar(s,dato.fecha(),false));r.guardar(p);
        r.auditar(usuario,empresa,"PRESUPUESTO",p.getId(),"CREAR",null,estado(p));return ficha(p);
    }
    private void editable(Presupuesto p) { if(!p.isGestionado()||!p.getEstado().getDescripcion().equals("PENDIENTE")) throw ApiException.conflicto("Solo se modifican presupuestos pendientes creados por gestion"); }
    @Transactional public PresupuestoFicha actualizarPresupuesto(int usuario,int empresa,int id,PresupuestoEntrada dato) {
        comenzar(usuario,empresa,"PRESUPUESTOS");var p=r.presupuesto(empresa,id);version(dato.version(),p.getVersionGestion());editable(p);
        if(!Objects.equals(dato.serieId(),p.getSerie().getId())||!Objects.equals(dato.clienteId(),p.getCliente().getId())||!Objects.equals(dato.obraId(),p.getObra()==null?null:p.getObra().getId())) throw ApiException.conflicto("Serie, cliente y obra son inmutables");
        var antes=estado(p);aplicar(usuario,p,dato);p.setVersionGestion(p.getVersionGestion()+1);r.auditar(usuario,empresa,"PRESUPUESTO",id,"ACTUALIZAR",antes,estado(p));return ficha(p);
    }
    private void aplicar(int usuario,Presupuesto p,PresupuestoEntrada dato) {
        fecha(dato.fecha(),"fecha");rango(dato.fecha(),dato.validez());if(dato.fecha().getYear()!=p.getSerie().getEjercicio()) throw ApiException.invalido("fecha");
        if(dato.lineas()==null||dato.lineas().isEmpty()||dato.lineas().size()>50) throw ApiException.invalido("lineas");
        var lineas=new ArrayList<LineaPresupuesto>();BigDecimal subtotal=new BigDecimal("0.00"),descuento=subtotal,impuestos=subtotal,total=subtotal;
        for(var entrada:dato.lineas()) {
            var calculada=CalculoFactura.linea(entrada,lineas.size()+1);var l=new LineaPresupuesto();l.setPresupuesto(p);l.setDescripcion(calculada.getDescripcion());l.setCantidad(calculada.getCantidad());l.setPrecio(calculada.getPrecio());
            l.setDescuento(calculada.getDescuento());l.setIva(calculada.getIva());l.setImporte(calculada.getTotal());l.setFechaCreacion(ahora());l.setFechaModificacion(ahora());l.setUsuarioCreacion(autor(usuario));l.setUsuarioModificacion(autor(usuario));lineas.add(l);
            subtotal=subtotal.add(calculada.getBruto());descuento=descuento.add(calculada.getRebaja());impuestos=impuestos.add(calculada.getCuota());total=total.add(calculada.getTotal());
        }
        p.setSubtotal(CalculoFactura.dinero(subtotal));p.setDescuento(CalculoFactura.dinero(descuento));p.setImpuestos(CalculoFactura.dinero(impuestos));p.setTotal(CalculoFactura.dinero(total));
        p.getLineas().clear();p.getLineas().addAll(lineas);p.setFecha(dato.fecha());p.setValidez(dato.validez());p.setFechaModificacion(ahora());p.setUsuarioModificacion(autor(usuario));
    }
    @Transactional public PresupuestoFicha estadoPresupuesto(int usuario,int empresa,int id,CambioPresupuesto dato) {
        comenzar(usuario,empresa,"PRESUPUESTOS");var p=r.presupuesto(empresa,id);version(dato.version(),p.getVersionGestion());editable(p);if(dato.estado()==null) throw ApiException.invalido("estado");
        var antes=estado(p);p.setEstado(r.estadoPresupuesto(dato.estado().name()));p.setVersionGestion(p.getVersionGestion()+1);p.setFechaModificacion(ahora());p.setUsuarioModificacion(autor(usuario));
        r.auditar(usuario,empresa,"PRESUPUESTO",id,"ESTADO",antes,estado(p));return ficha(p);
    }
}
