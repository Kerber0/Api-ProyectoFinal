package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresupuestoSummaryDTO {
    private Integer id;
    private String numero;
    private LocalDate fecha;
    private BigDecimal total;
    private String nombreCliente;
}
