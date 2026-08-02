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
public class LogResponseDTO {
    private Integer id;
    private LocalDateTime fecha;
    private String accion;
    private String tablaAfectada;
    private Integer idRegistro;
    private String descripcion;
    private String ip;

    // --- Relación ---
    private UsuarioSummaryDTO usuario;
}
