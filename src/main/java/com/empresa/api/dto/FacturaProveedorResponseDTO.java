package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacturaProveedorResponseDTO {
    private Integer id;
    private String numero;
    private LocalDate fecha;
    private LocalDate fechaVencimiento;
    private String observaciones;
    private BigDecimal subtotal;
    private BigDecimal descuento;
    private BigDecimal impuestos;
    private BigDecimal total;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // --- Relaciones ---
    private SerieDocumentoSummaryDTO serie;
    private EstadoFacturaSummaryDTO estado;
    private ProveedorSummaryDTO proveedor;

    // --- Lista anidada ---
    private List<LineaFacturaProveedorResponseDTO> lineas;
}
