package com.empresa.api.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClienteUpdateDTO {

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

    // --- Campos de Cliente ---
    @Size(max = 20)
    private String codigoCliente;

    @PositiveOrZero
    private BigDecimal descuento;

    @PositiveOrZero
    private BigDecimal limiteCredito;

    private Integer idZona;

    private Integer idFormaPago;
}
