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
public class DireccionResponseDTO {
    private Integer id;
    private String tipoDireccion;
    private String direccion;
    private String codigoPostal;
    private String poblacion;
    private String provincia;
    private String pais;
    private String telefono;
    private String observaciones;
    private Boolean principal;
    private Boolean activo;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // --- Relación ---
    private EntidadSummaryDTO entidad;
}
