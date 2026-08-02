package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermisoSummaryDTO {
    private Integer id;
    private String nombre;
    private String nivelAcceso; // Se llenará desde la tabla Rol_Permiso
}
