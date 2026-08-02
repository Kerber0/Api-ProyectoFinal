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
public class TareaSummaryDTO {
    private Integer id;
    private String titulo;
    private LocalDate fechaLimite;
    private String estado;
    private String prioridad;
}
