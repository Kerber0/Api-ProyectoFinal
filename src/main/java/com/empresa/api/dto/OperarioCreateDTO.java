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
public class OperarioCreateDTO {

    // --- Campos de Entidad (solo persona física) ---
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    private String nombre;

    @Size(max = 150)
    private String apellidos;

    @NotBlank(message = "El NIF es obligatorio")
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

    @NotNull
    private Boolean activo;

    // --- Campos de Operario ---
    @NotBlank(message = "El código de operario es obligatorio")
    @Size(max = 20)
    private String codigoOperario;

    @NotNull(message = "La fecha de alta es obligatoria")
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
