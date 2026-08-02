package com.empresa.api.dto;

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
public class ObraUpdateDTO {

    @Size(max = 20)
    private String codigoObra;

    @Size(max = 200)
    private String titulo;

    private String descripcion;

    @PositiveOrZero
    private BigDecimal presupuesto;

    private LocalDate fechaInicio;
    private LocalDate fechaFinPrevista;
    private LocalDate fechaFinReal;

    private Integer idEstado;

    private String observaciones;

    private Integer idCliente;

    private Integer idDireccion;
}
