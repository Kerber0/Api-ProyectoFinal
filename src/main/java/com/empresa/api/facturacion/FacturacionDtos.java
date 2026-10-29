package com.empresa.api.facturacion;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.*;
import java.util.List;

public final class FacturacionDtos {
    private FacturacionDtos() {}
    public enum Tipo { CLIENTE, PROVEEDOR }
    public record NuevaSerie(String nombre,String prefijo,Integer ejercicio,Integer longitud,Boolean rectificativa) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record EntradaLinea(String descripcion,String cantidad,String precio,String descuento,String iva) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record Nuevo(Integer serieId,Integer terceroId,Integer obraId,LocalDate fecha,LocalDate vencimiento,String referenciaExterna,List<EntradaLinea> lineas) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record Rectificar(Integer serieId,LocalDate fecha,String referenciaExterna,String motivo) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record Anular(String motivo) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record Serie(int id,String nombre,String prefijo,int ejercicio,int longitud,boolean rectificativa,int siguienteNumero,LocalDate ultimaFecha) {}
    public record Linea(int posicion,String descripcion,String cantidad,String precio,String descuento,String iva,String bruto,String rebaja,String base,String cuota,String total) {}
    public record Resumen(int id,int empresaId,String tipo,String estado,int serieId,Integer facturaId,String numero,LocalDate fecha,Integer originalId,String total) {}
    public record Detalle(int id,int empresaId,String tipo,String estado,int serieId,int terceroId,Integer obraId,LocalDate fecha,LocalDate vencimiento,
                          String referenciaExterna,Integer originalId,String motivo,Integer facturaId,String numero,String moneda,String regla,
                          String subtotal,String descuento,String impuestos,String total,String empresaNombre,String empresaNif,String terceroNombre,String terceroNif,
                          int autorId,Instant creado,Instant emitido,List<Linea> lineas) {}
}
