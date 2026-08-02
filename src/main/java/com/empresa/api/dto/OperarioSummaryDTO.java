package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperarioSummaryDTO {
    private Integer id;
    private String codigoOperario;
    private String nombreCompleto;
    private String nifCif;
}
