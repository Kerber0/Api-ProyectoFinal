package com.empresa.api.laboral;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class EmpleoDtos {
    private EmpleoDtos() {}
    public enum TipoAusencia { VACACIONES, PERMISO, BAJA, FALTA, OTRA }
    public record Vincular(@NotNull @Positive Integer usuarioId) {}
    public record Vinculo(int usuarioId, int operarioId) {}
    public record NuevaAsignacion(@NotNull @Positive Integer obraId, @NotNull LocalDate desde, LocalDate hasta) {}
    public record Asignacion(int id, int operarioId, int obraId, LocalDate desde, LocalDate hasta, boolean cancelada) {}
    public record NuevaAusencia(@NotNull LocalDate desde, @NotNull LocalDate hasta,
                                @NotNull TipoAusencia tipo, Boolean justificada,
                                @Size(max=1000) String observaciones) {
        // Constructor compatible con los tests y llamadas previas.
        public NuevaAusencia(LocalDate desde, LocalDate hasta, TipoAusencia tipo) {
            this(desde, hasta, tipo, null, null);
        }
    }
    public record AusenciaDetallePrivado(int id, int operarioId, LocalDate desde, LocalDate hasta,
                                         TipoAusencia tipo, boolean cancelada,
                                         Boolean justificada, String observaciones) {}
    public record AusenciaDetalle(int id, int operarioId, LocalDate desde, LocalDate hasta, TipoAusencia tipo, boolean cancelada) {}
    public record Empleado(int id, String codigo, String nombre) {}
    public record Ubicacion(String direccion, String codigoPostal, String poblacion, String provincia, String pais) {}
    public record ObraOperativa(int id, String codigo, String titulo, String estado,
                                LocalDate fechaInicio, LocalDate fechaFinPrevista, Ubicacion ubicacion) {}
    public record ContactoOperativo(int id, String nombre, String apellidos, String telefono, String movil) {}
}