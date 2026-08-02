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
public class AsignarContactoObraDTO {
    @NotNull
    private Integer idObra;
    @NotNull
    private Integer idContacto;
    @NotBlank
    @Size(max = 80)
    private String rolEnObra;
    private String observaciones;
}
