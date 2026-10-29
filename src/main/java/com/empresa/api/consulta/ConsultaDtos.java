package com.empresa.api.consulta;

import java.time.LocalDate;

public final class ConsultaDtos {
    private ConsultaDtos() {}
    public record ClienteResumen(int id, String codigo, String nombre, boolean activo) {}
    public record ClienteDetalle(int id, String codigo, String nombre, String tipoEntidad,
                                 String nifCif, String telefono, String movil, String email,
                                 String observaciones, String ibanEnmascarado, boolean activo) {}
    public record ObraResumen(int id, String codigo, String titulo, String estado, int clienteId) {}
    public record ReferenciaCliente(int id, String codigo, String nombre) {}
    public record Direccion(int id, String tipoDireccion, String direccion, String codigoPostal, String poblacion,
                            String provincia, String pais, String telefono, String observaciones, boolean principal) {}
    public record ObraDetalle(int id, String codigo, String titulo, String descripcion, String presupuesto,
                              LocalDate fechaInicio, LocalDate fechaFinPrevista, LocalDate fechaFinReal,
                              String estado, ReferenciaCliente cliente, Direccion direccion) {}
    public record Contacto(int id, String nombre, String apellidos, String cargo, String telefono,
                           String movil, String email, String departamento, String observaciones, boolean principal) {}
    public record ContactoObra(int id, Contacto contacto, String rol) {}
}
