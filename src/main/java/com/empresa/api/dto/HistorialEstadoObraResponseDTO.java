package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialEstadoObraResponseDTO {
    private Integer id;
    private LocalDateTime fecha;
    private String observaciones;

    // --- Relaciones ---
    private ObraSummaryDTO obra;
    private EstadoObraSummaryDTO estadoAnterior;
    private EstadoObraSummaryDTO estadoNuevo;
    private UsuarioSummaryDTO usuario;
}
