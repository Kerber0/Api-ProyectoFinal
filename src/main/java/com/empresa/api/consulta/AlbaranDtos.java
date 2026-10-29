package com.empresa.api.consulta;

import java.time.LocalDate;

public final class AlbaranDtos {
    private AlbaranDtos() {}
    public record Tercero(int id, String codigo, String nombre) {}
    public record Albaran(int id, String tipo, String numero, LocalDate fecha, String estado,
                          Tercero tercero, Integer facturaId,
                          String subtotal, String descuento, String impuestos, String total) {}
    public record Linea(int id, int albaranId, String tipo, String referenciaArticulo, String descripcion,
                        String cantidad, String precio, String descuento, String iva, String importe) {}
}
