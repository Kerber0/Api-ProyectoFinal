package com.empresa.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormaPagoCreateDTO {

    @NotBlank(message = "La descripción no puede estar vacía")
    @Size(max = 300, message = "La descripción no puede exceder los 300 caracteres")
    private String descripcion;

    @NotNull(message = "Los días de vencimiento son obligatorios")
    @PositiveOrZero(message = "Los días de vencimiento no pueden ser negativos")
    private Integer diasVencimiento;

    @NotNull(message = "El estado 'activo' es obligatorio")
    private Boolean activo;
}
