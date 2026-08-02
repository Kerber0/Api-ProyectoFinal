package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParteManoObraResponseDTO {
    private Integer id;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private BigDecimal horasTrabajadas;
    private BigDecimal costeHora;
    private BigDecimal costeTotal;
    private String observaciones;
    private LocalDateTime fechaCreacion;

    // --- Relaciones ---
    private ObraSummaryDTO obra;
    private OperarioSummaryDTO operario;
}
