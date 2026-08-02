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
public class ArticuloCreateDTO {

    @NotBlank
    @Size(max = 50)
    private String referencia;

    @Size(max = 50)
    private String codigoBarras;

    @NotBlank
    @Size(max = 300)
    private String descripcion;

    @Size(max = 100)
    private String marca;

    @Size(max = 100)
    private String modelo;

    @NotNull
    @PositiveOrZero
    private BigDecimal precioCoste;

    @NotNull
    @PositiveOrZero
    private BigDecimal precioVenta;

    @NotNull
    @PositiveOrZero
    private BigDecimal stockMinimo;

    @NotNull
    @PositiveOrZero
    private BigDecimal stockMaximo;

    @PositiveOrZero
    private BigDecimal peso;

    @Size(max = 100)
    private String ubicacion;

    @NotNull
    private Boolean activo;

    private Integer idFamilia;
    private Integer idIva;
    private Integer idUnidad;
    private Integer idProveedor;
}
