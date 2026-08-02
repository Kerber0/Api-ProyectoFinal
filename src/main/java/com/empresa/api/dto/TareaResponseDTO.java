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
public class TareaResponseDTO {
    private Integer id;
    private String titulo;
    private String descripcion;
    private LocalDate fechaLimite;
    private LocalDate fechaFinalizacion;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // --- Relaciones ---
    private PrioridadSummaryDTO prioridad;
    private EstadoTareaSummaryDTO estado;
    private ObraSummaryDTO obra;
    private OperarioSummaryDTO operario;
}
