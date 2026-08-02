package com.empresa.api.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoStockCreateDTO {

    @NotBlank
    private String referenciaArticulo;

    @NotNull
    private Integer idAlmacen;

    @NotNull
    private LocalDateTime fecha;

    @NotBlank
    @Size(max = 20)
    private String tipoMovimiento;

    @Size(max = 100)
    private String origen;

    @Size(max = 100)
    private String destino;

    @Size(max = 50)
    private String documento;

    @NotNull
    private BigDecimal cantidad;

    @PositiveOrZero
    private BigDecimal precioUnitario;

    private String observaciones;
}
