package com.empresa.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineaFacturaClienteCreateDTO {

    private String referenciaArticulo;

    @NotBlank(message = "La descripción de la línea es obligatoria")
    @Size(max = 300)
    private String descripcion;

    @NotNull
    @Positive(message = "La cantidad debe ser positiva")
    private BigDecimal cantidad;

    @NotNull
    @PositiveOrZero(message = "El precio no puede ser negativo")
    private BigDecimal precio;

    @NotNull
    @PositiveOrZero(message = "El descuento no puede ser negativo")
    private BigDecimal descuento;

    @NotNull
    @PositiveOrZero(message = "El IVA no puede ser negativo")
    private BigDecimal iva;
}
