package com.empresa.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProveedorResponseDTO {

    // --- Campos de Proveedor ---
    private Integer id;
    private String codigoProveedor;
    private String tipoProveedor;
    private String iban;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;

    // --- Campos aplanados de Entidad ---
    private String tipoEntidad;
    private String nombre;
    private String apellidos;
    private String razonSocial;
    private String nombreComercial;
    private String nifCif;
    private String telefonoFijo;
    private String movil;
    private String email;
    private String web;
    private LocalDate fechaNacimiento;
    private String observaciones;
    private Boolean activo;
    private LocalDate fechaAlta;
}
