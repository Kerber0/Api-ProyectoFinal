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
public class UnidadMedidaUpdateDTO {

    @Size(max = 300)
    private String descripcion;

    @Size(max = 10)
    private String abreviatura;

    private Boolean activo;
}
