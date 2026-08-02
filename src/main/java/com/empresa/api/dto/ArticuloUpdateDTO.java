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
public class ArticuloUpdateDTO {

    @Size(max = 50)
    private String codigoBarras;

    @Size(max = 300)
    private String descripcion;

    @Size(max = 100)
    private String marca;

    @Size(max = 100)
    private String modelo;

    @PositiveOrZero
    private BigDecimal precioCoste;

    @PositiveOrZero
    private BigDecimal precioVenta;

    @PositiveOrZero
    private BigDecimal stockMinimo;

    @PositiveOrZero
    private BigDecimal stockMaximo;

    @PositiveOrZero
    private BigDecimal peso;

    @Size(max = 100)
    private String ubicacion;

    private Boolean activo;

    private Integer idFamilia;
    private Integer idIva;
    private Integer idUnidad;
    private Integer idProveedor;
}
