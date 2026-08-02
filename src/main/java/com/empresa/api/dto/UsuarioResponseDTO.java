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
public class UsuarioResponseDTO {
    private Integer id;
    private String nombreUsuario;
    private String emailLogin;
    private Boolean activo;
    private LocalDateTime ultimoLogin;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
    // El campo entidad se mapeará a un EntidadSummaryDTO
    private EntidadSummaryDTO entidad;
}
