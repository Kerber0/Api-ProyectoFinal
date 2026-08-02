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
public class EventoResponseDTO {
    private Integer id;
    private String titulo;
    private String descripcion;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String color;
    private String tipoEvento;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // --- Relaciones ---
    private ObraSummaryDTO obra;
    private UsuarioSummaryDTO usuarioCreacion;
}
