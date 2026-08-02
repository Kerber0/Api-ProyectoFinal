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
public class ObraResponseDTO {
    private Integer id;
    private String codigoObra;
    private String titulo;
    private String descripcion;
    private BigDecimal presupuesto;
    private LocalDate fechaInicio;
    private LocalDate fechaFinPrevista;
    private LocalDate fechaFinReal;
    private String observaciones;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // --- Relaciones ---
    private SerieDocumentoSummaryDTO serie;
    private EstadoObraSummaryDTO estado;
    private ClienteSummaryDTO cliente;
    private DireccionSummaryDTO direccion;
}
