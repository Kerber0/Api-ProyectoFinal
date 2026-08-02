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
public class RolPermisoResponseDTO {
    private Integer id;
    private RolSummaryDTO rol;
    private PermisoSummaryDTO permiso;
    private String nivelAcceso;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
}
