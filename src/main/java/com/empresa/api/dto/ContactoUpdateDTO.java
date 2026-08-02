package com.empresa.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactoUpdateDTO {

    @Size(max = 100)
    private String nombre;

    @Size(max = 150)
    private String apellidos;

    @Size(max = 100)
    private String cargo;

    @Size(max = 100)
    private String departamento;

    @Size(max = 20)
    private String telefono;

    @Size(max = 20)
    private String movil;

    @Email
    @Size(max = 150)
    private String email;

    private String observaciones;

    private Boolean principal;

    private Boolean activo;
}
