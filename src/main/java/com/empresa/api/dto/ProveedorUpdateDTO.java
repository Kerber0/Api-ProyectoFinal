package com.empresa.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProveedorUpdateDTO {

    // --- Campos de Entidad ---
    @Size(max = 100)
    private String nombre;

    @Size(max = 150)
    private String apellidos;

    @Size(max = 150)
    private String razonSocial;

    @Size(max = 150)
    private String nombreComercial;

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

    private Boolean activo;

    private LocalDate fechaAlta;

    // --- Campos de Proveedor ---
    @Size(max = 20)
    private String codigoProveedor;

    @Size(max = 20)
    private String tipoProveedor;

    @Size(max = 34)
    private String iban;
}
