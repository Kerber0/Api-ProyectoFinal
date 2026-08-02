package com.empresa.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
public class OperarioUpdateDTO {

    // --- Campos de Entidad ---
    @Size(max = 100)
    private String nombre;

    @Size(max = 150)
    private String apellidos;

    @Size(max = 20)
    private String nifCif;

    @Size(max = 20)
    private String telefonoFijo;

    @Size(max = 20)
    private String movil;

    @Email
    @Size(max = 150)
    private String email;

    @Past
    private LocalDate fechaNacimiento;

    private String observaciones;

    private Boolean activo;

    // --- Campos de Operario ---
    @Size(max = 20)
    private String codigoOperario;

    private LocalDate fechaAlta;

    private LocalDate fechaBaja;

    @Size(max = 30)
    private String nss;

    @Positive
    private BigDecimal horasSemanales;

    @Positive
    private BigDecimal costeHoraReal;

    private Integer idCategoria;
}
