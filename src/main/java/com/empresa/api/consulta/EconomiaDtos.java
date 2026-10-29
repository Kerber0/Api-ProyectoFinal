package com.empresa.api.consulta;

import java.time.LocalDate;

public final class EconomiaDtos {
    private EconomiaDtos() {}
    public record Tercero(int id, String codigo, String nombre) {}
    public record Factura(int id, int empresaId, String tipo, String numero, LocalDate fecha, LocalDate fechaVencimiento,
                          String estadoOriginal, Tercero tercero, Integer obraId,
                          String subtotal, String descuento, String impuestos, String total) {}
    public record Linea(int id, int facturaId, String descripcion, String cantidad, String precio,
                        String descuento, String iva, String importe) {}
    public record Movimiento(int id, int facturaId, LocalDate fecha, String importe, String metodoPago) {}
    public record Saldo(int facturaId, String tipo, String total, String movimientosRegistrados,
                        String saldoAritmetico, String regla) {}
    public record Vencimiento(int facturaId, String tipo, String numero, LocalDate fechaVencimiento,
                              String total, String movimientosRegistrados, String saldoAritmetico, String regla) {}
}
