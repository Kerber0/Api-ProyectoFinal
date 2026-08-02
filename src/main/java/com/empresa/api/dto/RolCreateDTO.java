package com.empresa.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolCreateDTO {

    @NotBlank
    @Size(max = 50)
    private String nombre;

    @Size(max = 300)
    private String descripcion;

    @NotNull
    private Boolean activo;
}
