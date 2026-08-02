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
public class DireccionUpdateDTO {

    @Size(max = 50)
    private String tipoDireccion;

    @Size(max = 200)
    private String direccion;

    @Size(max = 10)
    private String codigoPostal;

    @Size(max = 100)
    private String poblacion;

    @Size(max = 100)
    private String provincia;

    @Size(max = 100)
    private String pais;

    @Size(max = 20)
    private String telefono;

    private String observaciones;

    private Boolean principal;

    private Boolean activo;
}
