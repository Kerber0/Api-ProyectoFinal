package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagoResponseDTO {
    private Integer id;
    private LocalDate fecha;
    private BigDecimal importe;
    private String metodoPago;
    private String numeroOperacion;
    private String observaciones;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // --- Relación ---
    private FacturaProveedorSummaryDTO factura;
}
