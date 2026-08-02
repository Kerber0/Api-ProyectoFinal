package com.empresa.api.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoUpdateDTO {

    @Size(max = 150)
    private String titulo;

    private String descripcion;

    private LocalDateTime fechaInicio;

    private LocalDateTime fechaFin;

    @Size(max = 20)
    private String color;

    @Size(max = 30)
    private String tipoEvento;

    private Integer idObra;
}
