package com.empresa.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class MaterialPorObraCreateDTO {

    @NotNull
    private Integer idObra;

    @NotBlank
    private String referenciaArticulo;

    private Integer idAlmacen;

    @NotNull
    private LocalDate fecha;

    @NotNull
    @Positive
    private BigDecimal cantidad;

    private String observaciones;
}
