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
public class ContactoResponseDTO {
    private Integer id;
    private String nombre;
    private String apellidos;
    private String cargo;
    private String departamento;
    private String telefono;
    private String movil;
    private String email;
    private String observaciones;
    private Boolean principal;
    private Boolean activo;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // --- Relación ---
    private EntidadSummaryDTO entidad;
}
