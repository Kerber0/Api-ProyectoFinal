package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoriaOperarioSummaryDTO {
    private Integer id;
    private String descripcion;
    private BigDecimal costeHoraBase;
}
