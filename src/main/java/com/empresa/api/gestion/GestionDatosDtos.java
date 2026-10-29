package com.empresa.api.gestion;

import jakarta.validation.constraints.*;

public final class GestionDatosDtos {
    private GestionDatosDtos() {}

    public enum TipoTitular { CLIENTE, PROVEEDOR, OPERARIO }

    public record ContactoEntrada(
            @NotBlank @Size(max=100) String nombre,
            @Size(max=150) String apellidos,
            @Size(max=100) String cargo,
            @Size(max=100) String departamento,
            @Size(max=20) String telefono,
            @Size(max=20) String movil,
            @Email @Size(max=150) String email,
            @Size(max=2000) String observaciones,
            @NotNull Boolean principal,
            @NotNull Boolean activo) {}

    public record ContactoFicha(int id, int titularId, String nombre, String apellidos,
                                String cargo, String departamento, String telefono,
                                String movil, String email, String observaciones,
                                boolean principal, boolean activo) {}

    public record DireccionEntrada(
            @NotBlank @Size(max=50) String tipoDireccion,
            @NotBlank @Size(max=200) String direccion,
            @Size(max=10) String codigoPostal,
            @Size(max=100) String poblacion,
            @Size(max=100) String provincia,
            @NotBlank @Size(max=100) String pais,
            @Size(max=20) String telefono,
            @Size(max=2000) String observaciones,
            @NotNull Boolean principal,
            @NotNull Boolean activo) {}

    public record DireccionFicha(int id, int titularId, String tipoDireccion, String direccion,
                                 String codigoPostal, String poblacion, String provincia, String pais,
                                 String telefono, String observaciones, boolean principal, boolean activo) {}
}
