package com.empresa.api.consulta;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CalendarioDtos {
    private CalendarioDtos() {}
    public record Evento(int id, String titulo, LocalDateTime fechaInicio, LocalDateTime fechaFin, String tipo, Integer obraId) {}
    public record EventoDetalle(int id, String titulo, LocalDateTime fechaInicio, LocalDateTime fechaFin, String tipo, Integer obraId,
                                String descripcion) {}
    public record Tarea(int id, String titulo, LocalDate fechaLimite, LocalDate fechaFinalizacion, String estado, String prioridad,
                        Integer obraId, Integer operarioId) {}
    public record TareaDetalle(int id, String titulo, LocalDate fechaLimite, LocalDate fechaFinalizacion, String estado, String prioridad,
                               Integer obraId, Integer operarioId, String descripcion) {}
}
