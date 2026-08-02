package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DireccionSummaryDTO {
    private Integer id;
    private String direccion;
    private String poblacion;
    private String provincia;
    private String codigoPostal;
}
