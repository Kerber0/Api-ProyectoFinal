package com.empresa.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class TareaCreateDTO {

    @NotBlank
    @Size(max = 150)
    private String titulo;

    private String descripcion;

    @NotNull
    private Integer idPrioridad;

    @NotNull
    private Integer idEstado;

    private LocalDate fechaLimite;

    private Integer idObra;

    private Integer idOperario;
}
