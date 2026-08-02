package com.empresa.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CategoriaOperarioCreateDTO {

    @NotBlank(message = "La descripción no puede estar vacía")
    @Size(max = 300)
    private String descripcion;

    @NotNull(message = "El coste por hora es obligatorio")
    @Positive(message = "El coste por hora debe ser positivo")
    private BigDecimal costeHoraBase;

    @NotNull(message = "El estado 'activo' es obligatorio")
    private Boolean activo;
}
