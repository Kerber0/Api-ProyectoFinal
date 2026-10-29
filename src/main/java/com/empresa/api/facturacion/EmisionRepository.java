package com.empresa.api.facturacion;

import com.empresa.api.model.*;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.ArrayList;

/** Materializa una unica vez el agregado calculado en las entidades economicas existentes. */
@Repository
public class EmisionRepository {
    private final EntityManager entities;
    public EmisionRepository(EntityManager entities) { this.entities=entities; }
    public int emitirCliente(FacturaGestion d,SerieDocumento serie,String numero,int autor,Instant instante,int estado) {
        var fecha=LocalDateTime.ofInstant(instante,ZoneOffset.UTC);
        var usuario=entities.getReference(Usuario.class,autor);
        var f=new FacturaCliente();
        f.setEmpresa(entities.getReference(Empresa.class,d.getEmpresa()));
        f.setCliente(entities.getReference(Cliente.class,d.getCliente()));
        if(d.getObra()!=null) f.setObra(entities.getReference(Obra.class,d.getObra()));
        f.setSerie(serie); f.setNumero(numero); f.setFecha(d.getFecha()); f.setFechaVencimiento(d.getVencimiento());
        f.setEstado(entities.getReference(EstadoFactura.class,estado));
        f.setSubtotal(d.getSubtotal()); f.setDescuento(d.getDescuento()); f.setImpuestos(d.getImpuestos()); f.setTotal(d.getTotal());
        f.setFechaCreacion(fecha); f.setFechaModificacion(fecha); f.setUsuarioCreacion(usuario); f.setUsuarioModificacion(usuario);
        var lineas=new ArrayList<LineaFacturaCliente>();
        for(var entrada:d.getLineas()) {
            var l=new LineaFacturaCliente(); l.setFactura(f); l.setDescripcion(entrada.getDescripcion());
            l.setCantidad(entrada.getCantidad()); l.setPrecio(entrada.getPrecio()); l.setDescuento(entrada.getDescuento()); l.setIva(entrada.getIva());
            l.setImporte(entrada.getTotal()); l.setFechaCreacion(fecha); l.setFechaModificacion(fecha);
            l.setUsuarioCreacion(usuario); l.setUsuarioModificacion(usuario); lineas.add(l);
        }
        f.setLineas(lineas); entities.persist(f); entities.flush(); return f.getId();
    }
    public int emitirProveedor(FacturaGestion d,SerieDocumento serie,String numero,int autor,Instant instante,int estado) {
        var fecha=LocalDateTime.ofInstant(instante,ZoneOffset.UTC);
        var usuario=entities.getReference(Usuario.class,autor);
        var f=new FacturaProveedor();
        f.setEmpresa(entities.getReference(Empresa.class,d.getEmpresa()));
        f.setProveedor(entities.getReference(Proveedor.class,d.getProveedor()));

        f.setSerie(serie); f.setNumero(numero); f.setFecha(d.getFecha()); f.setFechaVencimiento(d.getVencimiento());
        f.setEstado(entities.getReference(EstadoFactura.class,estado));
        f.setSubtotal(d.getSubtotal()); f.setDescuento(d.getDescuento()); f.setImpuestos(d.getImpuestos()); f.setTotal(d.getTotal());
        f.setFechaCreacion(fecha); f.setFechaModificacion(fecha); f.setUsuarioCreacion(usuario); f.setUsuarioModificacion(usuario);
        var lineas=new ArrayList<LineaFacturaProveedor>();
        for(var entrada:d.getLineas()) {
            var l=new LineaFacturaProveedor(); l.setFactura(f); l.setDescripcion(entrada.getDescripcion());
            l.setCantidad(entrada.getCantidad()); l.setPrecio(entrada.getPrecio()); l.setDescuento(entrada.getDescuento()); l.setIva(entrada.getIva());
            l.setImporte(entrada.getTotal()); l.setFechaCreacion(fecha); l.setFechaModificacion(fecha);
            l.setUsuarioCreacion(usuario); l.setUsuarioModificacion(usuario); lineas.add(l);
        }
        f.setLineas(lineas); entities.persist(f); entities.flush(); return f.getId();
    }
}
