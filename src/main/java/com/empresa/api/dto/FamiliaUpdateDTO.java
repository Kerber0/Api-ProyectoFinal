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
public class FamiliaUpdateDTO {

    @Size(max = 300)
    private String descripcion;

    private Boolean activo;
}
