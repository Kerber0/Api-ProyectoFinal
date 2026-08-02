package com.empresa.api.dto;

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
public class TipoIVAUpdateDTO {

    @Size(max = 300, message = "La descripción no puede exceder los 300 caracteres")
    private String descripcion;

    @PositiveOrZero(message = "El porcentaje de IVA no puede ser negativo")
    private BigDecimal porcentaje;

    private Boolean activo;
}
