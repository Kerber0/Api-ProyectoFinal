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
public class UsuarioRolResponseDTO {
    private Integer id;
    private UsuarioSummaryDTO usuario;
    private RolSummaryDTO rol;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
}
