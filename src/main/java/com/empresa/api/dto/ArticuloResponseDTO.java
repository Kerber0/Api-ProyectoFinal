package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticuloResponseDTO {
    private String referencia;
    private String codigoBarras;
    private String descripcion;
    private String marca;
    private String modelo;
    private BigDecimal precioCoste;
    private BigDecimal precioVenta;
    private BigDecimal stockMinimo;
    private BigDecimal stockMaximo;
    private BigDecimal peso;
    private String ubicacion;
    private Boolean activo;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // --- Relaciones ---
    private FamiliaSummaryDTO familia;
    private TipoIVASummaryDTO iva;
    private UnidadMedidaSummaryDTO unidad;
    private ProveedorSummaryDTO proveedor;
}
