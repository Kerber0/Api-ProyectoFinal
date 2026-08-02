package com.empresa.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacturaClienteCreateDTO {

    @NotNull(message = "El ID de la serie es obligatorio")
    private Integer idSerie;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    private LocalDate fechaVencimiento;
    private String observaciones;

    @NotNull(message = "El ID del estado es obligatorio")
    private Integer idEstado;

    @NotNull(message = "El ID del cliente es obligatorio")
    private Integer idCliente;

    private Integer idObra;

    @Valid // Para que se validen los DTOs de la lista
    @NotNull(message = "La lista de líneas no puede ser nula")
    private List<LineaFacturaClienteCreateDTO> lineas;
}
