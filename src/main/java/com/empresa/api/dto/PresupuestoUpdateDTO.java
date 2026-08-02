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
public class PresupuestoUpdateDTO {
    private LocalDate fecha;
    private LocalDate validez;
    private String observaciones;
    private Integer idEstado;
    private Integer idCliente;
    private Integer idObra;
}
