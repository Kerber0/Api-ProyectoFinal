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
public class IncidenciaUpdateDTO {
    private Integer idOperario;
    private LocalDate fecha;
    private String descripcion;
    private Integer idEstado;
    private Integer idPrioridad;
}
