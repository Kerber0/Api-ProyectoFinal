package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentoGeneralCreateDTO {
    private String descripcion;
    // El idUsuario se tomará del contexto de seguridad
}
