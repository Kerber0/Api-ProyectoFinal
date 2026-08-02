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
public class VehiculoCreateDTO {

    @NotBlank
    @Size(max = 15)
    private String matricula;

    @NotBlank
    @Size(max = 100)
    private String marca;

    @NotBlank
    @Size(max = 100)
    private String modelo;

    @Size(max = 100)
    private String version;

    @Size(max = 30)
    private String combustible;

    @Size(max = 30)
    private String color;

    private LocalDate fechaMatriculacion;

    @NotNull
    @PositiveOrZero
    private Integer kilometros;

    private LocalDate fechaItv;
    private LocalDate fechaSeguro;

    @Size(max = 100)
    private String numeroPoliza;

    @Size(max = 100)
    private String companiaSeguro;

    private String observaciones;

    @NotNull
    private Boolean activo;
}
