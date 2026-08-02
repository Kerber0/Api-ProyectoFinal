package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactoSummaryDTO {
    private Integer id;
    private String nombreCompleto;
    private String email;
    private String telefono;
}
