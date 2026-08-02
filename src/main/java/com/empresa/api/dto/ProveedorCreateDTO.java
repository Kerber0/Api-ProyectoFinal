package com.empresa.api.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProveedorCreateDTO {

    // --- Campos de Entidad ---
    @NotBlank(message = "El tipo de entidad es obligatorio ('PERSONA' o 'EMPRESA')")
    @Size(max = 10)
    private String tipoEntidad;

    @Size(max = 100)
    private String nombre;

    @Size(max = 150)
    private String apellidos;

    @Size(max = 150)
    private String razonSocial;

    @Size(max = 150)
    private String nombreComercial;

    @NotBlank(message = "El NIF/CIF es obligatorio")
    @Size(max = 20)
    private String nifCif;

    @Size(max = 20)
    private String telefonoFijo;

    @Size(max = 20)
    private String movil;

    @Email
    @Size(max = 150)
    private String email;

    @Size(max = 150)
    private String web;

    @Past
    private LocalDate fechaNacimiento;

    private String observaciones;

    @NotNull
    private Boolean activo;

    @NotNull
    private LocalDate fechaAlta;

    // --- Campos de Proveedor ---
    @NotBlank(message = "El código de proveedor es obligatorio")
    @Size(max = 20)
    private String codigoProveedor;

    @NotBlank(message = "El tipo de proveedor es obligatorio ('EMPRESA' o 'AUTONOMO')")
    @Size(max = 20)
    private String tipoProveedor;

    @Size(max = 34)
    private String iban;
}
