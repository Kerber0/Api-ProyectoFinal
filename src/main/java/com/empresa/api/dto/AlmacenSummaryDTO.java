package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlmacenSummaryDTO {
    private Integer id;
    private String codigoAlmacen;
    private String descripcion;
}
