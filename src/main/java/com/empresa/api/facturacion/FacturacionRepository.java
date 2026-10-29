package com.empresa.api.facturacion;

import com.empresa.api.common.*;
import com.empresa.api.model.*;
import jakarta.persistence.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static com.empresa.api.facturacion.FacturacionDtos.*;

@Repository
public class FacturacionRepository {
    private final EntityManager entities;
    private final JdbcTemplate jdbc;
    public FacturacionRepository(EntityManager entities,JdbcTemplate jdbc) { this.entities=entities; this.jdbc=jdbc; }
    public void serializar() { jdbc.query("SELECT pg_advisory_xact_lock(1146049102,4)",rs->{}); }
    public void guardar(Object o) { entities.persist(o); entities.flush(); }
    public void flush() { entities.flush(); }
    public SerieDocumento serie(int empresa,Tipo tipo,int id) {
        return entities.createQuery("select s from SerieDocumento s where s.id=:id and s.empresa=:empresa and s.tipoDocumento=:tipo and s.activo=true",SerieDocumento.class)
                .setParameter("id",id).setParameter("empresa",empresa).setParameter("tipo","FACTURA_"+tipo)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst().orElseThrow(ApiException::noEncontrado);
    }
    public FacturaGestion documento(int empresa,Tipo tipo,int id) {
        return entities.createQuery("select d from FacturaGestion d where d.id=:id and d.empresa=:empresa and d.tipo=:tipo",FacturaGestion.class)
                .setParameter("id",id).setParameter("empresa",empresa).setParameter("tipo",tipo.name())
                .getResultStream().findFirst().orElseThrow(ApiException::noEncontrado);
    }
    public void ambito(FacturaGestion d,boolean activo) {
        String tabla=d.getTipo().equals("CLIENTE")?"Clientes":"Proveedores", id=d.getTipo().equals("CLIENTE")?"id_cliente":"id_proveedor";
        Integer tercero=d.getCliente()!=null?d.getCliente():d.getProveedor();
        String pertenencia=d.getCliente()!=null?"API_Cliente_Empresa":"API_Proveedor_Empresa";
        int count=jdbc.queryForObject("SELECT count(*) FROM "+tabla+" t JOIN "+pertenencia+" p ON p."+id+"=t."+id+" JOIN Entidades e ON e.id_entidad=t.id_entidad WHERE p.id_empresa=? AND t."+id+"=?"+(activo?" AND e.activo=true":""),Integer.class,d.getEmpresa(),tercero);
        if(count!=1) throw ApiException.noEncontrado();
        if(d.getObra()!=null && jdbc.queryForObject("SELECT count(*) FROM Obras o JOIN API_Obra_Empresa e ON e.id_obra=o.id_obra WHERE o.id_obra=? AND e.id_empresa=? AND o.id_cliente=?",Integer.class,d.getObra(),d.getEmpresa(),d.getCliente())!=1) throw ApiException.noEncontrado();
    }
    public void identidad(FacturaGestion d) {
        var empresa=jdbc.query("SELECT e.razon_social,e.nombre,e.apellidos,e.nif_cif FROM API_Empresas p JOIN Entidades e ON e.id_entidad=p.id_entidad WHERE p.id_empresa=? AND p.activo=true",(rs,n)->new String[]{rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)},d.getEmpresa());
        if(empresa.size()!=1) throw ApiException.conflicto("Completar la identidad fiscal de la empresa");
        String tabla=d.getCliente()!=null?"Clientes":"Proveedores", id=d.getCliente()!=null?"id_cliente":"id_proveedor";
        var tercero=jdbc.query("SELECT e.razon_social,e.nombre,e.apellidos,e.nif_cif FROM "+tabla+" t JOIN Entidades e ON e.id_entidad=t.id_entidad WHERE t."+id+"=?",(rs,n)->new String[]{rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4)},d.getCliente()!=null?d.getCliente():d.getProveedor());
        d.setEmpresaNombre(nombre(empresa.getFirst())); d.setEmpresaNif(CalculoFactura.texto(empresa.getFirst()[3],20,"empresaNif"));
        d.setTerceroNombre(nombre(tercero.getFirst())); d.setTerceroNif(CalculoFactura.texto(tercero.getFirst()[3],20,"terceroNif"));
    }
    private String nombre(String[] e) { return CalculoFactura.texto(e[0]!=null&&!e[0].isBlank()?e[0]:((e[1]==null?"":e[1])+" "+(e[2]==null?"":e[2])),250,"nombreFiscal"); }
    public boolean serieDuplicada(int empresa,Tipo tipo,NuevaSerie n) {
        return jdbc.queryForObject("SELECT count(*) FROM Series_Documentos WHERE id_empresa=? AND tipo_documento=? AND ejercicio=? AND (nombre=? OR prefijo=?)",Integer.class,empresa,"FACTURA_"+tipo,n.ejercicio(),n.nombre(),n.prefijo())>0;
    }
    public boolean referenciaDuplicada(FacturaGestion d) {
        return d.getProveedor()!=null && jdbc.queryForObject("SELECT count(*) FROM API_Facturacion WHERE id_empresa=? AND proveedor=? AND referencia_externa=? AND estado<>'ANULADA' AND id<>?",Integer.class,d.getEmpresa(),d.getProveedor(),d.getReferenciaExterna(),d.getId()==null?0:d.getId())>0;
    }
    public boolean tieneRectificacion(int original) { return jdbc.queryForObject("SELECT count(*) FROM API_Facturacion WHERE original=? AND estado<>'ANULADA'",Integer.class,original)>0; }
    public int estadoEmitida() { return jdbc.queryForObject("SELECT id_estado FROM Estados_Factura WHERE descripcion='EMITIDA_GESTION'",Integer.class); }
    public void auditar(Integer documento,Integer serie,int autor,String accion,Instant fecha) {
        jdbc.update("INSERT INTO API_Facturacion_Auditoria(documento,serie,autor,accion,fecha,motivo) VALUES (?,?,?,?,?,(SELECT motivo FROM API_Facturacion WHERE id=?))",documento,serie,autor,accion,Timestamp.from(fecha),documento);
    }
    public Pagina<Serie> series(int empresa,Tipo tipo,int page,int size) {
        String where=" FROM Series_Documentos WHERE id_empresa=? AND tipo_documento=?";
        long count=jdbc.queryForObject("SELECT count(*)"+where,Long.class,empresa,"FACTURA_"+tipo);
        var items=jdbc.query("SELECT id_serie,nombre,prefijo,ejercicio,longitud,rectificativa,siguiente_numero,ultima_fecha"+where+" ORDER BY id_serie LIMIT ? OFFSET ?",
                (rs,n)->new Serie(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getInt(5),rs.getBoolean(6),rs.getInt(7),rs.getObject(8,LocalDate.class)),empresa,"FACTURA_"+tipo,size,(long)page*size);
        return new Pagina<>(items,page,size,count,(count+size-1)/size,"id,asc");
    }
    public Pagina<Resumen> documentos(int empresa,Tipo tipo,int page,int size) {
        String where=" FROM API_Facturacion WHERE id_empresa=? AND tipo=?";
        long count=jdbc.queryForObject("SELECT count(*)"+where,Long.class,empresa,tipo.name());
        var items=jdbc.query("SELECT id,tipo,estado,id_serie,COALESCE(factura_cliente,factura_proveedor),numero,fecha,original,total"+where+" ORDER BY id LIMIT ? OFFSET ?",
                (rs,n)->new Resumen(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getObject(5,Integer.class),rs.getString(6),rs.getObject(7,LocalDate.class),rs.getObject(8,Integer.class),rs.getBigDecimal(9).toPlainString()),empresa,tipo.name(),size,(long)page*size);
        return new Pagina<>(items,page,size,count,(count+size-1)/size,"id,asc");
    }
}
