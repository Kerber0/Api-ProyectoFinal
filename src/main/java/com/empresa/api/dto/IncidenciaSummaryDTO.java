package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidenciaSummaryDTO {
    private Integer id;
    private LocalDate fecha;
    private String descripcionCorta;
    private String estado;
    private String prioridad;
}
