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
public class ObraContactoResponseDTO {
    private Integer id;
    private String rolEnObra;
    private String observaciones;
    private LocalDateTime fechaCreacion;

    // --- Relaciones ---
    private ObraSummaryDTO obra;
    private ContactoSummaryDTO contacto;
}
