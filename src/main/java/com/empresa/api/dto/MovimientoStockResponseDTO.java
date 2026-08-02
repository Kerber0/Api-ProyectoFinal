package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoStockResponseDTO {
    private Integer id;
    private LocalDateTime fecha;
    private String tipoMovimiento;
    private String origen;
    private String destino;
    private String documento;
    private BigDecimal cantidad;
    private BigDecimal precioUnitario;
    private String observaciones;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // --- Relaciones ---
    private ArticuloSummaryDTO articulo;
    private AlmacenSummaryDTO almacen;
}
