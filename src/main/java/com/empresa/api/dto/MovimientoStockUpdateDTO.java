package com.empresa.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
public class MovimientoStockUpdateDTO {
    private LocalDateTime fecha;
    private String tipoMovimiento;
    private String origen;
    private String destino;
    private String documento;
    private BigDecimal cantidad;
    @PositiveOrZero
    private BigDecimal precioUnitario;
    private String observaciones;
}
