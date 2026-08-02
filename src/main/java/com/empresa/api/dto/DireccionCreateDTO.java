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
public class DireccionCreateDTO {

    @NotNull
    private Integer idEntidad;

    @NotBlank
    @Size(max = 50)
    private String tipoDireccion;

    @NotBlank
    @Size(max = 200)
    private String direccion;

    @Size(max = 10)
    private String codigoPostal;

    @Size(max = 100)
    private String poblacion;

    @Size(max = 100)
    private String provincia;

    @NotBlank
    @Size(max = 100)
    private String pais;

    @Size(max = 20)
    private String telefono;

    private String observaciones;

    @NotNull
    private Boolean principal;

    @NotNull
    private Boolean activo;
}
