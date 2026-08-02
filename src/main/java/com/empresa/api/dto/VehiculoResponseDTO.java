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
public class VehiculoResponseDTO {
    private Integer id;
    private String matricula;
    private String marca;
    private String modelo;
    private String version;
    private String combustible;
    private String color;
    private LocalDate fechaMatriculacion;
    private Integer kilometros;
    private LocalDate fechaItv;
    private LocalDate fechaSeguro;
    private String numeroPoliza;
    private String companiaSeguro;
    private String observaciones;
    private Boolean activo;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
}
