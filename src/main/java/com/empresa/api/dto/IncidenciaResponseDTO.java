package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidenciaResponseDTO {
    private Integer id;
    private LocalDate fecha;
    private String descripcion;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // --- Relaciones ---
    private ObraSummaryDTO obra;
    private OperarioSummaryDTO operario;
    private EstadoIncidenciaSummaryDTO estado;
    private PrioridadSummaryDTO prioridad;
}
