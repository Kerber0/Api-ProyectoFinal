package com.empresa.api.gestion;

import jakarta.validation.constraints.*;

public final class GestionContactosObraDtos {
    private GestionContactosObraDtos() {}
    public record Asignar(@NotNull @Positive Integer contactoId,
                          @NotBlank @Size(max=80) String rol,
                          @Size(max=1000) String observaciones) {}
    public record Editar(@NotBlank @Size(max=80) String rol,
                         @Size(max=1000) String observaciones) {}
    public record Relacion(int id,int obraId,int contactoId,String rol,String observaciones) {}
}
