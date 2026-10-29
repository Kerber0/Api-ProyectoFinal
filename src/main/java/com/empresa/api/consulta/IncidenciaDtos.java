package com.empresa.api.consulta;

import java.time.LocalDate;

public final class IncidenciaDtos {
    private IncidenciaDtos() {}
    public record Resumen(int id, int obraId, Integer operarioId, LocalDate fecha, String estado, String prioridad) {}
    public record Detalle(int id, int obraId, Integer operarioId, LocalDate fecha, String estado, String prioridad,
                          String descripcion) {}
}
