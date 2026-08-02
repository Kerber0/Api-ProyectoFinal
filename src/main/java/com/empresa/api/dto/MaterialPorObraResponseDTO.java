package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialPorObraResponseDTO {
    private Integer id;
    private LocalDate fecha;
    private BigDecimal cantidad;
    private BigDecimal costeUnitario;
    private BigDecimal importe;
    private String observaciones;
    private LocalDateTime fechaCreacion;

    // --- Relaciones ---
    private ObraSummaryDTO obra;
    private ArticuloSummaryDTO articulo;
    private AlmacenSummaryDTO almacen;
}
