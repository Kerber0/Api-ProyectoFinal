package com.empresa.api.tesoreria;

import com.empresa.api.common.*;
import com.empresa.api.model.*;
import com.empresa.api.facturacion.FacturacionDtos.Tipo;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static com.empresa.api.tesoreria.TesoreriaDtos.*;

@Repository
public class TesoreriaRepository {
    private final EntityManager entities;
    private final JdbcTemplate jdbc;
    public TesoreriaRepository(EntityManager entities,JdbcTemplate jdbc) { this.entities=entities; this.jdbc=jdbc; }
    public void guardar(Object o) { entities.persist(o); entities.flush(); }
    public void flush() { entities.flush(); }
    public List<VencimientoGestion> plan(int documento) {
        return entities.createQuery("select v from VencimientoGestion v where v.documento=:id order by v.posicion",VencimientoGestion.class).setParameter("id",documento).getResultList();
    }
    public VencimientoGestion vencimiento(int empresa,Tipo tipo,int id) {
        return entities.createQuery("select v from VencimientoGestion v, FacturaGestion d where v.documento=d.id and v.id=:id and d.empresa=:empresa and d.tipo=:tipo",VencimientoGestion.class)
                .setParameter("id",id).setParameter("empresa",empresa).setParameter("tipo",tipo.name()).getResultStream().findFirst().orElseThrow(ApiException::noEncontrado);
    }
    public MovimientoTesoreria movimiento(int empresa,Tipo tipo,int id) {
        return entities.createQuery("select m from MovimientoTesoreria m where m.id=:id and m.empresa=:empresa and m.tipo=:tipo",MovimientoTesoreria.class)
                .setParameter("id",id).setParameter("empresa",empresa).setParameter("tipo",tipo.name()).getResultStream().findFirst().orElseThrow(ApiException::noEncontrado);
    }
    public Optional<MovimientoTesoreria> porClave(int empresa,Tipo tipo,String clave) {
        return entities.createQuery("select m from MovimientoTesoreria m where m.empresa=:empresa and m.tipo=:tipo and m.clave=:clave",MovimientoTesoreria.class)
                .setParameter("empresa",empresa).setParameter("tipo",tipo.name()).setParameter("clave",clave).getResultStream().findFirst();
    }
    public boolean referenciaUsada(int empresa,Tipo tipo,String referencia) {
        return jdbc.queryForObject("SELECT count(*) FROM API_Tesoreria WHERE id_empresa=? AND tipo=? AND referencia=?",Integer.class,empresa,tipo.name(),referencia)>0;
    }
    public List<AplicacionTesoreria> aplicaciones(int movimiento) {
        return entities.createQuery("select a from AplicacionTesoreria a where a.movimiento=:id order by a.vencimiento",AplicacionTesoreria.class).setParameter("id",movimiento).getResultList();
    }
    public BigDecimal aplicado(int vencimiento) {
        return jdbc.queryForObject("SELECT COALESCE(sum(a.importe),0.00) FROM API_Tesoreria_Aplicaciones a JOIN API_Tesoreria m ON m.id=a.movimiento WHERE a.vencimiento=? AND m.estado<>'REVERTIDO'",BigDecimal.class,vencimiento);
    }
    public void comprobarHistorico(FacturaGestion d) {
        String tabla=d.getTipo().equals("CLIENTE")?"Cobros":"Pagos";
        int factura=d.getFacturaCliente()!=null?d.getFacturaCliente():d.getFacturaProveedor();
        BigDecimal registrado=jdbc.queryForObject("SELECT COALESCE(sum(importe),0.00) FROM "+tabla+" WHERE id_factura=?",BigDecimal.class,factura);
        BigDecimal aplicado=jdbc.queryForObject("SELECT COALESCE(sum(a.importe),0.00) FROM API_Tesoreria_Aplicaciones a JOIN API_Vencimientos v ON v.id=a.vencimiento JOIN API_Tesoreria m ON m.id=a.movimiento WHERE v.documento=? AND m.estado<>'REVERTIDO'",BigDecimal.class,d.getId());
        if(registrado.compareTo(aplicado)!=0) throw ApiException.conflicto("La factura tiene movimientos ajenos al reparto; requiere revision explicita");
    }
    public int economico(FacturaGestion d,BigDecimal importe,LocalDate fecha,String metodo,int autor,Instant ahora) {
        var usuario=entities.getReference(Usuario.class,autor);
        var instante=LocalDateTime.ofInstant(ahora,ZoneOffset.UTC);
        if(d.getTipo().equals("CLIENTE")) {
            var c=new Cobro(); c.setFactura(entities.getReference(FacturaCliente.class,d.getFacturaCliente()));
            c.setImporte(importe); c.setFecha(fecha); c.setMetodoPago(metodo); c.setUsuarioCreacion(usuario); c.setUsuarioModificacion(usuario); c.setFechaCreacion(instante); c.setFechaModificacion(instante);
            guardar(c); return c.getId();
        }
        var p=new Pago(); p.setFactura(entities.getReference(FacturaProveedor.class,d.getFacturaProveedor()));
        p.setImporte(importe); p.setFecha(fecha); p.setMetodoPago(metodo); p.setUsuarioCreacion(usuario); p.setUsuarioModificacion(usuario); p.setFechaCreacion(instante); p.setFechaModificacion(instante);
        guardar(p); return p.getId();
    }
    public void auditar(Integer documento,Integer movimiento,int autor,String accion,Instant ahora,String detalle) {
        jdbc.update("INSERT INTO API_Tesoreria_Auditoria(documento,movimiento,autor,accion,fecha,detalle) VALUES (?,?,?,?,?,?)",documento,movimiento,autor,accion,Timestamp.from(ahora),detalle);
    }
    private static final String VENCIMIENTOS=" FROM API_Vencimientos v JOIN API_Facturacion d ON d.id=v.documento LEFT JOIN (SELECT a.vencimiento,sum(a.importe) AS aplicado FROM API_Tesoreria_Aplicaciones a JOIN API_Tesoreria m ON m.id=a.movimiento WHERE m.estado<>'REVERTIDO' GROUP BY a.vencimiento) x ON x.vencimiento=v.id WHERE d.id_empresa=? AND d.tipo=?";
    public Pagina<Vencimiento> vencimientos(int empresa,Tipo tipo,TesoreriaParametros p) {
        var args=new ArrayList<Object>(List.of(empresa,tipo.name())); String from=VENCIMIENTOS;
        if(p.documento()!=null) { from+=" AND d.id=?"; args.add(p.documento()); }
        if(p.tercero()!=null) { from+=" AND COALESCE(d.cliente,d.proveedor)=?"; args.add(p.tercero()); }
        if(p.desde()!=null) { from+=" AND v.fecha>=?"; args.add(p.desde()); }
        if(p.hasta()!=null) { from+=" AND v.fecha<=?"; args.add(p.hasta()); }
        if(p.pendientes()) from+=" AND v.importe<>COALESCE(x.aplicado,0.00)";
        long count=jdbc.queryForObject("SELECT count(*)"+from,Long.class,args.toArray());
        args.add(p.size()); args.add((long)p.page()*p.size());
        var rows=jdbc.query("SELECT v.id,d.id,COALESCE(d.factura_cliente,d.factura_proveedor),COALESCE(d.cliente,d.proveedor),v.posicion,v.fecha,v.importe,COALESCE(x.aplicado,0.00)"+from+" ORDER BY v.fecha,v.id LIMIT ? OFFSET ?",(rs,n)->new Vencimiento(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getObject(6,LocalDate.class),rs.getBigDecimal(7).toPlainString(),rs.getBigDecimal(8).toPlainString(),rs.getBigDecimal(7).subtract(rs.getBigDecimal(8)).toPlainString()),args.toArray());
        return new Pagina<>(rows,p.page(),p.size(),count,(count+p.size()-1)/p.size(),"fecha,asc;id,asc");
    }
    public Pagina<Resumen> movimientos(int empresa,Tipo tipo,TesoreriaParametros p) {
        long count=jdbc.queryForObject("SELECT count(*) FROM API_Tesoreria WHERE id_empresa=? AND tipo=?",Long.class,empresa,tipo.name());
        var rows=jdbc.query("SELECT id,tipo,tercero,fecha,importe,metodo,estado FROM API_Tesoreria WHERE id_empresa=? AND tipo=? ORDER BY id LIMIT ? OFFSET ?",(rs,n)->new Resumen(rs.getInt(1),rs.getString(2),rs.getInt(3),rs.getObject(4,LocalDate.class),rs.getBigDecimal(5).toPlainString(),rs.getString(6),rs.getString(7)),empresa,tipo.name(),p.size(),(long)p.page()*p.size());
        return new Pagina<>(rows,p.page(),p.size(),count,(count+p.size()-1)/p.size(),"id,asc");
    }
}
