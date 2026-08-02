package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObraSummaryDTO {
    private Integer id;
    private String codigoObra;
    private String titulo;
}
