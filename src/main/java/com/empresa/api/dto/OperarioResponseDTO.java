package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperarioResponseDTO {

    // --- Campos de Operario ---
    private Integer id;
    private String codigoOperario;
    private LocalDate fechaAlta;
    private LocalDate fechaBaja;
    private String nss;
    private BigDecimal horasSemanales;
    private BigDecimal costeHoraReal;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // --- Campos aplanados de Entidad ---
    private String nombre;
    private String apellidos;
    private String nifCif;
    private String telefonoFijo;
    private String movil;
    private String email;
    private LocalDate fechaNacimiento;
    private String observaciones;
    private Boolean activo;

    // --- Relaciones ---
    private CategoriaOperarioSummaryDTO categoria;
}
