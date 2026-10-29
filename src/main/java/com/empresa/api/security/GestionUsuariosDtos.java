package com.empresa.api.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.List;

/** Contratos minimos: no se reutilizan los borradores con entidad fiscal o email personal. */
public final class GestionUsuariosDtos {
    private GestionUsuariosDtos() {}
    public record Cuenta(int id, String usuario, boolean activo) {}
    public record Crear(@NotBlank @Size(max=50) String usuario,
                        @NotBlank @Size(min=15,max=72)
                        @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String password) {
        @Override public String toString() { return "Crear[credenciales omitidas]"; }
    }
    public record Actualizar(@NotBlank @Size(max=50) String usuario, @NotNull Boolean activo) {}
    public record Password(@NotBlank @Size(min=15,max=72)
                           @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) String password) {
        @Override public String toString() { return "Password[omitida]"; }
    }
    public record Permiso(int id, String nombre, boolean activo) {}
    public record Nivel(String permiso, String nivel) {}
    public record Rol(int id, String nombre, boolean activo, boolean global, List<Nivel> permisos) {}
    public record Asignacion(Integer empresa, int rol, String nombre, boolean global) {}
}
