package com.empresa.api.dto;

import jakarta.validation.constraints.*;
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
public class CobroCreateDTO {

    @NotNull
    private Integer idFactura;

    @NotNull
    private LocalDate fecha;

    @NotNull
    @Positive
    private BigDecimal importe;

    @Size(max = 20)
    private String metodoPago;

    @Size(max = 100)
    private String numeroOperacion;

    private String observaciones;
}
