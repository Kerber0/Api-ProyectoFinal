package com.empresa.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidenciaCreateDTO {

    @NotNull
    private Integer idObra;

    private Integer idOperario;

    @NotNull
    private LocalDate fecha;

    @NotBlank
    private String descripcion;

    @NotNull
    private Integer idEstado;

    @NotNull
    private Integer idPrioridad;
}
