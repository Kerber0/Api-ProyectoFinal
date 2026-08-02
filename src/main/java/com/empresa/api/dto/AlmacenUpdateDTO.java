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
public class AlmacenUpdateDTO {

    @Size(max = 20)
    private String codigoAlmacen;

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

    private Boolean activo;
}
