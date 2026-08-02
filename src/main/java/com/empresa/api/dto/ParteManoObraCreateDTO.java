package com.empresa.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParteManoObraCreateDTO {

    @NotNull
    private Integer idObra;

    @NotNull
    private Integer idOperario;

    @NotNull
    private LocalDate fecha;

    private LocalTime horaInicio;
    private LocalTime horaFin;

    @NotNull
    @Positive
    private BigDecimal horasTrabajadas;

    private String observaciones;
}
