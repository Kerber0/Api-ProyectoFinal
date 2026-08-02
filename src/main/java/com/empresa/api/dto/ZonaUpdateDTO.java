package com.empresa.api.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZonaUpdateDTO {

    @Size(max = 300, message = "La descripción no puede exceder los 300 caracteres")
    private String descripcion;

    private Boolean activo;
}
