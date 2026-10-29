package com.empresa.api.consulta;

import java.time.LocalDate;

public final class CompraDtos {
    private CompraDtos() {}
    public record Proveedor(int id, String codigo, String nombre, String tipo, boolean activo) {}
    public record ProveedorDetalle(int id, String codigo, String nombre, String tipo, String nifCif,
                                   String telefono, String movil, String email, String observaciones,
                                   String ibanEnmascarado, boolean activo) {}
    public record ReferenciaProveedor(int id, String codigo, String nombre) {}
    public record Pedido(int id, String numero, LocalDate fecha, String estado, ReferenciaProveedor proveedor,
                         String subtotal, String descuento, String impuestos, String total) {}
    public record Linea(int id, int pedidoId, String referenciaArticulo, String descripcion, String cantidad,
                        String precioCoste, String descuento, String iva, String importe) {}
}
