package com.empresa.api.operativa;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class OperativaDtos {
    private OperativaDtos() {}
    public record IncidenciaEntrada(@NotNull @Positive Integer obraId, @Positive Integer operarioId,
                                   @NotNull LocalDate fecha, @NotBlank @Size(max=5000) String descripcion,
                                   @NotNull @Positive Integer estadoId, @NotNull @Positive Integer prioridadId) {}
    public record IncidenciaFicha(int id,int empresaId,int obraId,Integer operarioId,LocalDate fecha,
                                 String descripcion,int estadoId,int prioridadId) {}
    public record TareaEntrada(@NotBlank @Size(max=150) String titulo, @Size(max=5000) String descripcion,
                               @NotNull @Positive Integer estadoId, @NotNull @Positive Integer prioridadId,
                               LocalDate fechaLimite,LocalDate fechaFinalizacion,
                               @Positive Integer obraId,@Positive Integer operarioId) {}
    public record TareaFicha(int id,int empresaId,String titulo,String descripcion,int estadoId,int prioridadId,
                             LocalDate fechaLimite,LocalDate fechaFinalizacion,Integer obraId,Integer operarioId) {}
    public record EventoEntrada(@NotBlank @Size(max=150) String titulo,@Size(max=5000) String descripcion,
                                @NotNull LocalDateTime fechaInicio,LocalDateTime fechaFin,
                                @Size(max=20) String color,@NotBlank @Size(max=30) String tipoEvento,
                                @Positive Integer obraId) {}
    public record EventoFicha(int id,int empresaId,String titulo,String descripcion,LocalDateTime fechaInicio,
                              LocalDateTime fechaFin,String color,String tipoEvento,Integer obraId) {}
}
