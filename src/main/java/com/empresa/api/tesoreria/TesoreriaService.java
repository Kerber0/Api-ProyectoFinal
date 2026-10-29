package com.empresa.api.tesoreria;

import com.empresa.api.common.*;
import com.empresa.api.facturacion.*;
import com.empresa.api.facturacion.FacturacionDtos.Tipo;
import com.empresa.api.model.*;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.empresa.api.tesoreria.TesoreriaDtos.*;

@Service @Transactional(readOnly=true)
public class TesoreriaService {
    private final TesoreriaRepository repository;
    private final FacturacionRepository facturacion;
    private final AutorizacionService acceso;
    private final Clock clock;
    public TesoreriaService(TesoreriaRepository repository,FacturacionRepository facturacion,AutorizacionService acceso,Clock clock) {
        this.repository=repository; this.facturacion=facturacion; this.acceso=acceso; this.clock=clock;
    }
    private void autorizar(int autor,int empresa,Tipo tipo,boolean escribir) {
        acceso.exigirLectura(autor,empresa,"EMPRESA");
        acceso.exigirLectura(autor,empresa,"FACTURAS_"+tipo);
        String permiso=tipo==Tipo.CLIENTE?"COBROS":"PAGOS";
        if(escribir) acceso.exigirEscritura(autor,empresa,permiso); else acceso.exigirLectura(autor,empresa,permiso);
    }
    private void comenzar(int autor,int empresa,Tipo tipo) {
        autorizar(autor,empresa,tipo,true); facturacion.serializar(); autorizar(autor,empresa,tipo,true);
    }
    private Instant ahora() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private static BigDecimal dinero(String valor) {
        if(valor==null||!valor.matches("-?[0-9]{1,10}(\\.[0-9]{1,2})?")) throw ApiException.invalido("importe");
        BigDecimal n=new BigDecimal(valor).setScale(2);
        if(n.signum()==0||n.abs().compareTo(new BigDecimal("9999999999.99"))>0) throw ApiException.invalido("importe");
        return n;
    }
    private void fechaMovimiento(LocalDate fecha) {
        if(fecha==null||fecha.isAfter(LocalDate.now(clock))) throw ApiException.invalido("fecha");
    }
    private FacturaGestion documento(int empresa,Tipo tipo,int id) {
        var d=facturacion.documento(empresa,tipo,id);
        if(!Set.of("EMITIDA","RECTIFICADA").contains(d.getEstado())) throw ApiException.conflicto("Se requiere una factura emitida");
        facturacion.ambito(d,false); return d;
    }
    private Vencimiento detalle(VencimientoGestion v,FacturaGestion d) {
        BigDecimal aplicado=repository.aplicado(v.getId());
        return new Vencimiento(v.getId(),d.getId(),d.getFacturaCliente()!=null?d.getFacturaCliente():d.getFacturaProveedor(),d.getCliente()!=null?d.getCliente():d.getProveedor(),v.getPosicion(),v.getFecha(),v.getImporte().toPlainString(),aplicado.toPlainString(),v.getImporte().subtract(aplicado).toPlainString());
    }
    private Movimiento detalle(MovimientoTesoreria m) {
        return new Movimiento(m.getId(),m.getTipo(),m.getTercero(),m.getClave(),m.getFecha(),m.getImporte().toPlainString(),m.getMetodo(),m.getEstado(),m.getReferencia(),m.getConciliado(),m.getMotivo(),m.getFechaReversion(),m.getAutor(),m.getCreado(),
                repository.aplicaciones(m.getId()).stream().map(a->new Aplicacion(a.getVencimiento(),a.getImporte().toPlainString(),a.getCobro()!=null?a.getCobro():a.getPago(),a.getCobroReversion()!=null?a.getCobroReversion():a.getPagoReversion())).toList());
    }
    public List<Vencimiento> plan(int autor,int empresa,Tipo tipo,int id) {
        autorizar(autor,empresa,tipo,false); var d=documento(empresa,tipo,id);
        return repository.plan(id).stream().map(v->detalle(v,d)).toList();
    }
    public Pagina<Vencimiento> vencimientos(int autor,int empresa,Tipo tipo,MultiValueMap<String,String> q) {
        autorizar(autor,empresa,tipo,false); return repository.vencimientos(empresa,tipo,TesoreriaParametros.parse(q,true));
    }
    public Pagina<Resumen> movimientos(int autor,int empresa,Tipo tipo,MultiValueMap<String,String> q) {
        autorizar(autor,empresa,tipo,false); return repository.movimientos(empresa,tipo,TesoreriaParametros.parse(q,false));
    }
    public Movimiento consultar(int autor,int empresa,Tipo tipo,int id) {
        autorizar(autor,empresa,tipo,false); return detalle(repository.movimiento(empresa,tipo,id));
    }
    @Transactional public List<Vencimiento> planificar(int autor,int empresa,Tipo tipo,int id,Plan request) {
        comenzar(autor,empresa,tipo); var d=documento(empresa,tipo,id); repository.comprobarHistorico(d);
        if(request.cuotas()==null||request.cuotas().isEmpty()||request.cuotas().size()>50) throw ApiException.invalido("cuotas");
        var nuevos=new ArrayList<VencimientoGestion>(); BigDecimal suma=new BigDecimal("0.00"); LocalDate anterior=d.getFecha();
        for(var cuota:request.cuotas()) {
            if(cuota==null||cuota.fecha()==null||cuota.fecha().isBefore(anterior)||cuota.fecha().getYear()>9999) throw ApiException.invalido("fecha");
            var importe=dinero(cuota.importe());
            if(importe.signum()!=d.getTotal().signum()) throw ApiException.invalido("importe");
            var v=new VencimientoGestion(); v.setDocumento(id); v.setPosicion(nuevos.size()+1); v.setFecha(cuota.fecha()); v.setImporte(importe); nuevos.add(v);
            suma=suma.add(importe); anterior=cuota.fecha();
        }
        if(suma.compareTo(d.getTotal())!=0) throw ApiException.conflicto("Los vencimientos deben sumar exactamente el total de la factura");
        var anteriores=repository.plan(id);
        if(!anteriores.isEmpty()) {
            boolean igual=anteriores.size()==nuevos.size();
            for(int i=0;igual&&i<nuevos.size();i++) igual=anteriores.get(i).getFecha().equals(nuevos.get(i).getFecha())&&anteriores.get(i).getImporte().compareTo(nuevos.get(i).getImporte())==0;
            if(!igual) throw ApiException.conflicto("El plan ya existe y es inmutable");
            return anteriores.stream().map(v->detalle(v,d)).toList();
        }
        nuevos.forEach(repository::guardar); repository.auditar(id,null,autor,"PLANIFICAR",ahora(),null);
        return nuevos.stream().map(v->detalle(v,d)).toList();
    }
    private static String huella(List<String> campos) {
        StringBuilder canonical=new StringBuilder(); for(String campo:campos) canonical.append(campo.length()).append(':').append(campo);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Transactional public Movimiento registrar(int autor,int empresa,Tipo tipo,NuevoMovimiento request) {
        comenzar(autor,empresa,tipo); fechaMovimiento(request.fecha()); BigDecimal importe=dinero(request.importe());
        if(request.clave()==null||!request.clave().matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) throw ApiException.invalido("clave");
        if(request.metodo()==null||!Set.of("EFECTIVO","TRANSFERENCIA","TARJETA","BIZUM","CHEQUE").contains(request.metodo())) throw ApiException.invalido("metodo");
        if(request.aplicaciones()==null||request.aplicaciones().isEmpty()||request.aplicaciones().size()>50) throw ApiException.invalido("aplicaciones");
        var reparto=new TreeMap<Integer,BigDecimal>(); BigDecimal suma=new BigDecimal("0.00");
        for(var a:request.aplicaciones()) {
            if(a==null||a.vencimientoId()==null||a.vencimientoId()<=0) throw ApiException.invalido("vencimientoId");
            var cantidad=dinero(a.importe());
            if(cantidad.signum()!=importe.signum()||reparto.put(a.vencimientoId(),cantidad)!=null) throw ApiException.invalido("aplicaciones");
            suma=suma.add(cantidad);
        }
        if(suma.compareTo(importe)!=0) throw ApiException.conflicto("El reparto debe sumar exactamente el importe del movimiento");
        var campos=new ArrayList<>(List.of(request.fecha().toString(),importe.toPlainString(),request.metodo()));
        reparto.forEach((id,cantidad)->{campos.add(id.toString()); campos.add(cantidad.toPlainString());}); String hash=huella(campos);
        var anterior=repository.porClave(empresa,tipo,request.clave());
        if(anterior.isPresent()) {
            if(!anterior.get().getHuella().equals(hash)) throw ApiException.conflicto("Clave ya utilizada con otro contenido");
            return detalle(anterior.get());
        }
        var documentos=new HashMap<Integer,FacturaGestion>(); Integer tercero=null;
        for(var a:reparto.entrySet()) {
            var v=repository.vencimiento(empresa,tipo,a.getKey()); var d=documento(empresa,tipo,v.getDocumento()); repository.comprobarHistorico(d);
            int t=d.getCliente()!=null?d.getCliente():d.getProveedor();
            if(tercero!=null&&tercero!=t) throw ApiException.conflicto("Todas las aplicaciones deben pertenecer al mismo tercero"); tercero=t;
            if(request.fecha().isBefore(d.getFecha())) throw ApiException.invalido("fecha");
            BigDecimal saldo=v.getImporte().subtract(repository.aplicado(v.getId()));
            if(a.getValue().signum()!=saldo.signum()||a.getValue().abs().compareTo(saldo.abs())>0) throw ApiException.conflicto("La aplicacion supera el saldo pendiente del vencimiento");
            documentos.put(v.getId(),d);
        }
        var now=ahora(); var m=new MovimientoTesoreria(); m.setEmpresa(empresa); m.setTipo(tipo.name()); m.setTercero(tercero); m.setClave(request.clave()); m.setHuella(hash); m.setFecha(request.fecha()); m.setImporte(importe); m.setMetodo(request.metodo()); m.setAutor(autor); m.setCreado(now); repository.guardar(m);
        for(var a:reparto.entrySet()) {
            int economico=repository.economico(documentos.get(a.getKey()),a.getValue(),m.getFecha(),m.getMetodo(),autor,now);
            var aplicacion=new AplicacionTesoreria(); aplicacion.setMovimiento(m.getId()); aplicacion.setVencimiento(a.getKey()); aplicacion.setImporte(a.getValue());
            if(tipo==Tipo.CLIENTE) aplicacion.setCobro(economico); else aplicacion.setPago(economico); repository.guardar(aplicacion);
        }
        repository.auditar(null,m.getId(),autor,"REGISTRAR",now,null); return detalle(m);
    }
    @Transactional public Movimiento conciliar(int autor,int empresa,Tipo tipo,int id,Conciliar request) {
        comenzar(autor,empresa,tipo); var m=repository.movimiento(empresa,tipo,id);
        String referencia=CalculoFactura.texto(request.referencia(),100,"referencia");
        if(m.getEstado().equals("CONCILIADO")&&referencia.equals(m.getReferencia())) return detalle(m);
        if(!m.getEstado().equals("REGISTRADO")||repository.referenciaUsada(empresa,tipo,referencia)) throw ApiException.conflicto("Estado o referencia de conciliacion en conflicto");
        m.setReferencia(referencia); m.setConciliado(ahora()); m.setEstado("CONCILIADO"); repository.flush(); repository.auditar(null,id,autor,"CONCILIAR",m.getConciliado(),referencia); return detalle(m);
    }
    @Transactional public Movimiento revertir(int autor,int empresa,Tipo tipo,int id,Revertir request) {
        comenzar(autor,empresa,tipo); var m=repository.movimiento(empresa,tipo,id); fechaMovimiento(request.fecha());
        String motivo=CalculoFactura.texto(request.motivo(),500,"motivo");
        if(request.fecha().isBefore(m.getFecha())) throw ApiException.invalido("fecha");
        if(m.getEstado().equals("REVERTIDO")) {
            if(motivo.equals(m.getMotivo())&&request.fecha().equals(m.getFechaReversion())) return detalle(m);
            throw ApiException.conflicto("La reversion ya existe con otro contenido");
        }
        var aplicaciones=repository.aplicaciones(id); var documentos=new HashMap<Integer,FacturaGestion>();
        for(var a:aplicaciones) {
            var v=repository.vencimiento(empresa,tipo,a.getVencimiento()); var d=documento(empresa,tipo,v.getDocumento()); repository.comprobarHistorico(d); documentos.put(v.getId(),d);
        }
        var now=ahora();
        for(var a:aplicaciones) {
            int economico=repository.economico(documentos.get(a.getVencimiento()),a.getImporte().negate(),request.fecha(),m.getMetodo(),autor,now);
            if(tipo==Tipo.CLIENTE) a.setCobroReversion(economico); else a.setPagoReversion(economico);
        }
        m.setEstado("REVERTIDO"); m.setMotivo(motivo); m.setFechaReversion(request.fecha()); repository.flush(); repository.auditar(null,id,autor,"REVERTIR",now,motivo); return detalle(m);
    }
}
