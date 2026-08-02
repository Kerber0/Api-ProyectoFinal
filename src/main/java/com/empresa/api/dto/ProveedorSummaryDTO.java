package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProveedorSummaryDTO {
    private Integer id;
    private String codigoProveedor;
    private String nombreCompleto; // Razón social o Nombre + Apellidos
    private String nifCif;
}
