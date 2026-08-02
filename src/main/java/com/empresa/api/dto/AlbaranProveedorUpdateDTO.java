package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbaranProveedorUpdateDTO {
    private LocalDate fecha;
    private String observaciones;
    private Integer idEstado;
    private Integer idProveedor;
    private Integer idPedido;
}
