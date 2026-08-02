package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlmacenResponseDTO {
    private Integer id;
    private String codigoAlmacen;
    private String descripcion;
    private String direccion;
    private String ciudad;
    private String provincia;
    private String codigoPostal;
    private String telefono;
    private Boolean activo;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
}
