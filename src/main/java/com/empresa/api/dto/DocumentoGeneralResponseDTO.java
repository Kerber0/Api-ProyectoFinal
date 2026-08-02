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
public class DocumentoGeneralResponseDTO {
    private Integer id;
    private String nombre;
    private String descripcion;
    private String ruta;
    private String tipo;
    private LocalDateTime fechaSubida;

    // --- Relaciones ---
    private UsuarioSummaryDTO usuario;
}
