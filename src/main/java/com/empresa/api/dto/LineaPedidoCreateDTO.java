package com.empresa.api.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineaPedidoCreateDTO {

    @NotBlank
    private String referenciaArticulo;

    @Size(max = 300)
    private String descripcion;

    @NotNull
    @Positive
    private BigDecimal cantidad;

    @NotNull
    @PositiveOrZero
    private BigDecimal precioCoste;

    @NotNull
    @PositiveOrZero
    private BigDecimal descuento;

    @NotNull
    @PositiveOrZero
    private BigDecimal iva;
}
