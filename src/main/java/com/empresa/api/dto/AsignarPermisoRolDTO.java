package com.empresa.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsignarPermisoRolDTO {
    @NotNull
    private Integer idRol;
    @NotNull
    private Integer idPermiso;
    @NotBlank
    private String nivelAcceso;
}
