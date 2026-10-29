package com.empresa.api.facturacion;

import com.empresa.api.common.*;
import com.empresa.api.model.*;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import static com.empresa.api.facturacion.FacturacionDtos.*;
import static com.empresa.api.facturacion.CalculoFactura.*;

@Service @Transactional(readOnly=true)
public class FacturacionService {
    private final FacturacionRepository repository;
    private final EmisionRepository emision;
    private final AutorizacionService autorizacion;
    private final Clock clock;
    public FacturacionService(FacturacionRepository repository,EmisionRepository emision,AutorizacionService autorizacion,Clock clock) {
        this.repository=repository; this.emision=emision; this.autorizacion=autorizacion; this.clock=clock;
    }
    private void autorizar(int autor,int empresa,Tipo tipo,boolean escribir) {
        autorizacion.exigirLectura(autor,empresa,"EMPRESA");
        if(escribir) autorizacion.exigirEscritura(autor,empresa,"FACTURAS_"+tipo);
        else autorizacion.exigirLectura(autor,empresa,"FACTURAS_"+tipo);
    }
    private void comenzar(int autor,int empresa,Tipo tipo) {
        autorizar(autor,empresa,tipo,true); repository.serializar(); autorizar(autor,empresa,tipo,true);
    }
    private Instant ahora() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static int id(Integer id,String campo) { if(id==null||id<=0) throw ApiException.invalido(campo); return id; }
    private void fecha(LocalDate f,SerieDocumento serie) {
        if(f==null || f.getYear()!=serie.getEjercicio() || f.isAfter(LocalDate.now(clock))) throw ApiException.invalido("fecha");
    }
    private void serieNormal(SerieDocumento serie,boolean rectificativa) {
        if(serie.getRectificativa()!=rectificativa) throw ApiException.conflicto("La naturaleza de la serie no corresponde al documento");
    }
    private static String s(BigDecimal n) { return n.toPlainString(); }
    private Detalle detalle(FacturaGestion d) {
        return new Detalle(d.getId(),d.getTipo(),d.getEstado(),d.getSerie(),d.getCliente()!=null?d.getCliente():d.getProveedor(),d.getObra(),d.getFecha(),d.getVencimiento(),
                d.getReferenciaExterna(),d.getOriginal(),d.getMotivo(),d.getFacturaCliente()!=null?d.getFacturaCliente():d.getFacturaProveedor(),d.getNumero(),"EUR","EUR_LINEA_HALF_UP_V1",
                s(d.getSubtotal()),s(d.getDescuento()),s(d.getImpuestos()),s(d.getTotal()),d.getEmpresaNombre(),d.getEmpresaNif(),d.getTerceroNombre(),d.getTerceroNif(),d.getAutor(),d.getCreado(),d.getEmitido(),
                d.getLineas().stream().map(l->new Linea(l.getPosicion(),l.getDescripcion(),s(l.getCantidad()),s(l.getPrecio()),s(l.getDescuento()),s(l.getIva()),s(l.getBruto()),s(l.getRebaja()),s(l.getBase()),s(l.getCuota()),s(l.getTotal()))).toList());
    }
    public Detalle consultar(int autor,int empresa,Tipo tipo,int id) { autorizar(autor,empresa,tipo,false); return detalle(repository.documento(empresa,tipo,id)); }
    public Pagina<Resumen> listar(int autor,int empresa,Tipo tipo,MultiValueMap<String,String> q) {
        autorizar(autor,empresa,tipo,false); int[] p=pagina(q); return repository.documentos(empresa,tipo,p[0],p[1]);
    }
    public Pagina<Serie> series(int autor,int empresa,Tipo tipo,MultiValueMap<String,String> q) {
        autorizar(autor,empresa,tipo,false); int[] p=pagina(q); return repository.series(empresa,tipo,p[0],p[1]);
    }
    private int[] pagina(MultiValueMap<String,String> q) {
        int page=0,size=20;
        for(var e:q.entrySet()) {
            if(!java.util.Set.of("page","size").contains(e.getKey()) || e.getValue().size()!=1) throw ApiException.invalido(e.getKey());
            try { String v=e.getValue().getFirst(); if(v==null||!v.matches("[0-9]{1,6}")) throw new NumberFormatException(); int n=Integer.parseInt(v);
                if(e.getKey().equals("page")) page=n; else size=n;
            } catch(NumberFormatException ex) { throw ApiException.invalido(e.getKey()); }
        }
        if(page>100000||size<1||size>100) throw ApiException.invalido("paginacion"); return new int[]{page,size};
    }
    @Transactional public Serie crearSerie(int autor,int empresa,Tipo tipo,NuevaSerie request) {
        comenzar(autor,empresa,tipo);
        String nombre=texto(request.nombre(),100,"nombre"),prefijo=texto(request.prefijo(),20,"prefijo");
        if(!prefijo.matches("[A-Z0-9-]{1,20}")||request.ejercicio()==null||request.ejercicio()<2000||request.ejercicio()>9999
                ||request.longitud()==null||request.longitud()<1||request.longitud()>9||request.rectificativa()==null) throw ApiException.invalido("serie");
        var normalized=new NuevaSerie(nombre,prefijo,request.ejercicio(),request.longitud(),request.rectificativa());
        if(repository.serieDuplicada(empresa,tipo,normalized)) throw ApiException.conflicto("Nombre o prefijo de serie ya utilizado en el ejercicio");
        var serie=new SerieDocumento(); serie.setEmpresa(empresa); serie.setTipoDocumento("FACTURA_"+tipo);
        serie.setNombre(nombre); serie.setPrefijo(prefijo); serie.setEjercicio(request.ejercicio()); serie.setLongitud(request.longitud());
        serie.setRectificativa(request.rectificativa()); serie.setSiguienteNumero(1); serie.setActivo(true);
        var now=ahora(); serie.setFechaCreacion(LocalDateTime.ofInstant(now,ZoneOffset.UTC)); serie.setFechaModificacion(serie.getFechaCreacion());
        repository.guardar(serie); repository.auditar(null,serie.getId(),autor,"CREAR_SERIE",now);
        return new Serie(serie.getId(),nombre,prefijo,serie.getEjercicio(),serie.getLongitud(),serie.getRectificativa(),1,null);
    }
    @Transactional public Detalle crear(int autor,int empresa,Tipo tipo,Nuevo request) {
        comenzar(autor,empresa,tipo);
        var serie=repository.serie(empresa,tipo,id(request.serieId(),"serieId")); serieNormal(serie,false); fecha(request.fecha(),serie);
        if(request.vencimiento()!=null&&request.vencimiento().isBefore(request.fecha())) throw ApiException.invalido("vencimiento");
        if(request.lineas()==null||request.lineas().isEmpty()||request.lineas().size()>50) throw ApiException.invalido("lineas");
        var d=new FacturaGestion(); d.setEmpresa(empresa); d.setTipo(tipo.name()); d.setSerie(serie.getId());
        int tercero=id(request.terceroId(),"terceroId");
        if(tipo==Tipo.CLIENTE) { d.setCliente(tercero); d.setObra(request.obraId()); if(request.referenciaExterna()!=null) throw ApiException.invalido("referenciaExterna"); }
        else { d.setProveedor(tercero); if(request.obraId()!=null) throw ApiException.invalido("obraId"); d.setReferenciaExterna(texto(request.referenciaExterna(),100,"referenciaExterna")); }
        d.setFecha(request.fecha()); d.setVencimiento(request.vencimiento()); d.setAutor(autor); d.setCreado(ahora());
        repository.ambito(d,true); repository.identidad(d);
        if(repository.referenciaDuplicada(d)) throw ApiException.conflicto("Referencia de proveedor ya registrada");
        BigDecimal subtotal=new BigDecimal("0.00"),descuento=subtotal,impuestos=subtotal,total=subtotal;
        for(var entrada:request.lineas()) {
            var l=linea(entrada,d.getLineas().size()+1); l.setDocumento(d); d.getLineas().add(l);
            subtotal=subtotal.add(l.getBruto()); descuento=descuento.add(l.getRebaja()); impuestos=impuestos.add(l.getCuota()); total=total.add(l.getTotal());
        }
        d.setSubtotal(dinero(subtotal)); d.setDescuento(dinero(descuento)); d.setImpuestos(dinero(impuestos)); d.setTotal(dinero(total));
        repository.guardar(d); repository.auditar(d.getId(),null,autor,"CREAR",d.getCreado()); return detalle(d);
    }
    @Transactional public Detalle emitir(int autor,int empresa,Tipo tipo,int id) {
        comenzar(autor,empresa,tipo); var d=repository.documento(empresa,tipo,id);
        if(d.getEstado().equals("EMITIDA")||d.getEstado().equals("RECTIFICADA")) return detalle(d);
        if(!d.getEstado().equals("BORRADOR")) throw ApiException.conflicto("El documento no es un borrador");
        var serie=repository.serie(empresa,tipo,d.getSerie()); serieNormal(serie,d.getOriginal()!=null); fecha(d.getFecha(),serie);
        repository.ambito(d,d.getOriginal()==null);
        if(d.getOriginal()==null) repository.identidad(d);
        if(serie.getUltimaFecha()!=null&&d.getFecha().isBefore(serie.getUltimaFecha())) throw ApiException.conflicto("La fecha precede a la ultima emision de la serie");
        int n=serie.getSiguienteNumero();
        if(n>=Math.pow(10,serie.getLongitud())) throw ApiException.conflicto("Serie agotada");
        String numero=serie.getPrefijo()+String.format(java.util.Locale.ROOT,"%0"+serie.getLongitud()+"d",n);
        var now=ahora();
        if(d.getOriginal()!=null) {
            var original=repository.documento(empresa,tipo,d.getOriginal());
            if(!original.getEstado().equals("EMITIDA")||original.getOriginal()!=null) throw ApiException.conflicto("La factura original no admite rectificacion");
            original.setEstado("RECTIFICADA"); repository.flush(); repository.auditar(original.getId(),null,autor,"RECTIFICAR",now);
        }
        int factura=tipo==Tipo.CLIENTE?emision.emitirCliente(d,serie,numero,autor,now,repository.estadoEmitida()):emision.emitirProveedor(d,serie,numero,autor,now,repository.estadoEmitida());
        if(tipo==Tipo.CLIENTE) d.setFacturaCliente(factura); else d.setFacturaProveedor(factura);
        d.setNumero(numero); d.setEstado("EMITIDA"); d.setEmitido(now);
        serie.setSiguienteNumero(n+1); serie.setUltimaFecha(d.getFecha()); serie.setFechaModificacion(LocalDateTime.ofInstant(now,ZoneOffset.UTC));
        repository.flush(); repository.auditar(d.getId(),null,autor,"EMITIR",now); return detalle(d);
    }
    @Transactional public Detalle anular(int autor,int empresa,Tipo tipo,int id,Anular request) {
        comenzar(autor,empresa,tipo); var d=repository.documento(empresa,tipo,id); String motivo=texto(request.motivo(),500,"motivo");
        if(d.getEstado().equals("ANULADA")&&motivo.equals(d.getMotivo())) return detalle(d);
        if(!d.getEstado().equals("BORRADOR")) throw ApiException.conflicto("Solo se anulan borradores; las emitidas requieren rectificacion");
        d.setEstado("ANULADA"); d.setMotivo(motivo); repository.flush(); repository.auditar(id,null,autor,"ANULAR",ahora()); return detalle(d);
    }
    @Transactional public Detalle rectificar(int autor,int empresa,Tipo tipo,int id,Rectificar request) {
        comenzar(autor,empresa,tipo); var original=repository.documento(empresa,tipo,id);
        if(!original.getEstado().equals("EMITIDA")||original.getOriginal()!=null||repository.tieneRectificacion(id)) throw ApiException.conflicto("La factura no admite otra rectificacion total");
        var serie=repository.serie(empresa,tipo,id(request.serieId(),"serieId")); serieNormal(serie,true); fecha(request.fecha(),serie);
        if(request.fecha().isBefore(original.getFecha())) throw ApiException.invalido("fecha");
        var d=new FacturaGestion(); d.setEmpresa(empresa); d.setTipo(tipo.name()); d.setSerie(serie.getId());
        d.setCliente(original.getCliente()); d.setProveedor(original.getProveedor()); d.setObra(original.getObra());
        d.setOriginal(id); d.setMotivo(texto(request.motivo(),500,"motivo")); d.setFecha(request.fecha()); d.setAutor(autor); d.setCreado(ahora());
        if(tipo==Tipo.PROVEEDOR) d.setReferenciaExterna(texto(request.referenciaExterna(),100,"referenciaExterna"));
        else if(request.referenciaExterna()!=null) throw ApiException.invalido("referenciaExterna");
        if(repository.referenciaDuplicada(d)) throw ApiException.conflicto("Referencia de proveedor ya registrada");
        d.setEmpresaNombre(original.getEmpresaNombre()); d.setEmpresaNif(original.getEmpresaNif()); d.setTerceroNombre(original.getTerceroNombre()); d.setTerceroNif(original.getTerceroNif());
        d.setSubtotal(original.getSubtotal().negate()); d.setDescuento(original.getDescuento().negate()); d.setImpuestos(original.getImpuestos().negate()); d.setTotal(original.getTotal().negate());
        for(var l:original.getLineas()) {
            var copy=new LineaFacturaGestion(); copy.setDocumento(d); copy.setPosicion(l.getPosicion()); copy.setDescripcion(l.getDescripcion());
            copy.setCantidad(l.getCantidad().negate()); copy.setPrecio(l.getPrecio()); copy.setDescuento(l.getDescuento()); copy.setIva(l.getIva());
            copy.setBruto(l.getBruto().negate()); copy.setRebaja(l.getRebaja().negate()); copy.setBase(l.getBase().negate()); copy.setCuota(l.getCuota().negate()); copy.setTotal(l.getTotal().negate()); d.getLineas().add(copy);
        }
        repository.guardar(d); repository.auditar(d.getId(),null,autor,"CREAR",d.getCreado()); return detalle(d);
    }
}
