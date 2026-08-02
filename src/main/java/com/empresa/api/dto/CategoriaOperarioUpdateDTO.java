package com.empresa.api.dto;

import jakarta.validation.constraints.Positive;
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
public class CategoriaOperarioUpdateDTO {

    @Size(max = 300)
    private String descripcion;

    @Positive(message = "El coste por hora debe ser positivo")
    private BigDecimal costeHoraBase;

    private Boolean activo;
}
