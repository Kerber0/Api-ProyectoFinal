package com.empresa.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsignarRolUsuarioDTO {
    @NotNull
    private Integer idUsuario;
    @NotNull
    private Integer idRol;
}
