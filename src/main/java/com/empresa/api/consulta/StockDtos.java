package com.empresa.api.consulta;

import java.time.LocalDateTime;

public final class StockDtos {
    private StockDtos() {}
    public record Articulo(String referencia, String descripcion, String marca, String modelo,
                           String unidad, boolean activo) {}
    public record Almacen(int id, String codigo, String descripcion, boolean activo) {}
    public record Existencia(String referencia, int almacenId, String cantidad, boolean calculable,
                             long movimientosPendientes, String regla) {}
    public record Movimiento(int id, String referencia, int almacenId, Integer almacenDestinoId,
                             LocalDateTime fecha, String tipo, String cantidad, String efectoOrigen,
                             boolean calculable) {}
}
