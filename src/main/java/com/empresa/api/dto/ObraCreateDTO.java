package com.empresa.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObraCreateDTO {

    @NotNull(message = "El ID de la serie es obligatorio")
    private Integer idSerie;

    @NotBlank(message = "El código de obra es obligatorio")
    @Size(max = 20)
    private String codigoObra;

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 200)
    private String titulo;

    private String descripcion;

    @NotNull
    @PositiveOrZero
    private BigDecimal presupuesto;

    private LocalDate fechaInicio;
    private LocalDate fechaFinPrevista;

    @NotNull(message = "El ID del estado es obligatorio")
    private Integer idEstado;

    private String observaciones;

    @NotNull(message = "El ID del cliente es obligatorio")
    private Integer idCliente;

    private Integer idDireccion;
}
