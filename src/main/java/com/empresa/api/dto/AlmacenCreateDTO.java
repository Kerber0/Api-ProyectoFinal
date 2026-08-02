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
public class AlmacenCreateDTO {

    @Size(max = 20)
    private String codigoAlmacen;

    @NotBlank
    @Size(max = 300)
    private String descripcion;

    @Size(max = 200)
    private String direccion;

    @Size(max = 100)
    private String ciudad;

    @Size(max = 100)
    private String provincia;

    @Size(max = 10)
    private String codigoPostal;

    @Size(max = 20)
    private String telefono;

    @NotNull
    private Boolean activo;
}
