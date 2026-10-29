package com.empresa.api.consulta;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ExpedienteDtos {
    private ExpedienteDtos() {}
    public record PresupuestoResumen(int id, String numero, LocalDate fecha, String estado,
                                      int clienteId, Integer obraId, String total) {}
    public record PresupuestoDetalle(int id, String numero, LocalDate fecha, LocalDate validez,
                                      String estado, int clienteId, Integer obraId, String subtotal,
                                      String descuento, String impuestos, String total, String formatoLineas) {}
    public record Linea(int id, int presupuestoId, String descripcion, String cantidad, String precio,
                        String descuento, String iva, String importe) {}
    public record Documento(int id, int obraId, String nombre, String tipo, LocalDateTime fechaSubida) {}
}
