package com.empresa.api.consulta;

import java.time.LocalDate;
import java.time.LocalTime;

public final class LaboralDtos {
    private LaboralDtos() {}
    public record Operario(int id, String codigo, String nombre, String categoria,
                           LocalDate fechaAlta, LocalDate fechaBaja) {}
    public record Parte(int id, int obraId, int operarioId, LocalDate fecha,
                        LocalTime horaInicio, LocalTime horaFin, String horasTrabajadas) {}
}
