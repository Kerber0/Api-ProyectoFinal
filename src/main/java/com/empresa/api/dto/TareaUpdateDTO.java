package com.empresa.api.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TareaUpdateDTO {

    @Size(max = 150)
    private String titulo;

    private String descripcion;

    private Integer idPrioridad;

    private Integer idEstado;

    private LocalDate fechaLimite;

    private LocalDate fechaFinalizacion;

    private Integer idObra;

    private Integer idOperario;
}
