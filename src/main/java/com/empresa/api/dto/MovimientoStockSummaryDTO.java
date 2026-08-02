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
public class MovimientoStockSummaryDTO {
    private Integer id;
    private LocalDateTime fecha;
    private String tipoMovimiento;
    private BigDecimal cantidad;
    private String referenciaArticulo;
    private String descripcionArticulo;
}
