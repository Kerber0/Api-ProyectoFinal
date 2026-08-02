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
public class DocumentoResponseDTO {
    private Integer id;
    private String nombre;
    private String ruta; // Podría ser una URL de descarga
    private String tipo; // Content-Type del fichero
    private LocalDateTime fechaSubida;
    private String observaciones;

    // --- Relaciones ---
    private ObraSummaryDTO obra;
    private UsuarioSummaryDTO usuarioCreacion;
}
