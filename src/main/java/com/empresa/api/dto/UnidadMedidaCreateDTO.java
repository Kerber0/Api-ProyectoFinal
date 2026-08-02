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
public class UnidadMedidaCreateDTO {

    @NotBlank
    @Size(max = 300)
    private String descripcion;

    @NotBlank
    @Size(max = 10)
    private String abreviatura;

    @NotNull
    private Boolean activo;
}
