package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LineaPedidoResponseDTO {
    private Integer id;
    private String descripcion;
    private BigDecimal cantidad;
    private BigDecimal precioCoste;
    private BigDecimal descuento;
    private BigDecimal iva;
    private BigDecimal importe;
    private ArticuloSummaryDTO articulo;
}
