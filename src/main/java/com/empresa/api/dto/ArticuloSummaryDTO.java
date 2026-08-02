package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticuloSummaryDTO {
    private String referencia;
    private String descripcion;
    private String marca;
    private String modelo;
}
