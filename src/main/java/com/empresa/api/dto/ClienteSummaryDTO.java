package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteSummaryDTO {
    private Integer id;
    private String codigoCliente;
    private String nombreCompleto; // Razón social o Nombre + Apellidos
    private String nifCif;
}
