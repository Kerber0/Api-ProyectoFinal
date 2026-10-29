package com.empresa.api.observaciones;

import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.Instant;

public final class ObservacionDtos {
    private ObservacionDtos() {}
    public enum Estado { ABIERTA, RESUELTA }
    public record Nueva(@NotBlank String texto) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record Resolver(@NotBlank String resolucion) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record Resumen(int id,int obraId,int autorId,Instant fechaCreacion,Estado estado,
                          Integer resueltoPor,Instant fechaResolucion) {}
    public record Detalle(int id,int obraId,int autorId,Instant fechaCreacion,Estado estado,
                          String texto,Integer resueltoPor,Instant fechaResolucion,String resolucion) {}
}
