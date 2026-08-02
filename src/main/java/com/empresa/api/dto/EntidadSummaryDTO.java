package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntidadSummaryDTO {
    private Integer id;
    private String nombreCompleto; // Combinación de nombre/apellidos o razón social
    private String nifCif;
}
