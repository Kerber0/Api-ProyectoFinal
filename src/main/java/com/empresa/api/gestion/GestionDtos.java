package com.empresa.api.gestion;

import java.time.LocalDate;
import java.util.List;
import com.empresa.api.facturacion.FacturacionDtos.EntradaLinea;

/** Sustituciones completas con version esperada. Los campos privados no pasan a lecturas Android. */
public final class GestionDtos {
    private GestionDtos() {}
    public record Identidad(String tipo, String nombre, String apellidos, String razonSocial,
                            String nif, String telefono, String movil, String email, String observaciones) {
        /** Conserva la construccion Java anterior; los nuevos datos son opcionales. */
        public Identidad(String tipo, String nombre, String apellidos, String razonSocial,
                         String nif, String telefono, String email) {
            this(tipo, nombre, apellidos, razonSocial, nif, telefono, null, email, null);
        }
    }
    public record ClienteEntrada(Long version, String codigo, Identidad identidad, String descuento,
                                 String limiteCredito, Integer zonaId, Integer formaPagoId) {}
    public record ClienteFicha(int id, long version, ClienteEntrada datos, boolean activo) {}
    public record ProveedorEntrada(Long version, String codigo, Identidad identidad, String tipo) {}
    public record ProveedorFicha(int id, long version, ProveedorEntrada datos, boolean activo) {}
    public record IbanEntrada(Long version, String iban) {}
    public record IbanFicha(long version, String iban) {}
    public record OperarioEntrada(Long version, String codigo, Identidad identidad, LocalDate alta,
                                  LocalDate baja, Integer categoriaId) {}
    public record OperarioFicha(int id, long version, OperarioEntrada datos, boolean activo) {}
    public record Actividad(Long version, Boolean activo) {}
    public enum TipoSerie { OBRA, PRESUPUESTO }
    public record SerieEntrada(String nombre, String prefijo, Integer ejercicio, Integer longitud) {}
    public record SerieFicha(int id, TipoSerie tipo, String nombre, String prefijo, int ejercicio,
                             int longitud, int siguienteNumero) {}
    public record ObraEntrada(Long version, Integer serieId, Integer clienteId, String titulo,
                              String descripcion, String presupuesto, LocalDate inicio, LocalDate finPrevista) {}
    public record ObraFicha(int id, long version, String codigo, String estado, LocalDate finReal, ObraEntrada datos) {}
    public enum EstadoObra { EN_CURSO, FINALIZADA, CANCELADA }
    public record CambioObra(Long version, EstadoObra estado, LocalDate fecha) {}
    public record PresupuestoEntrada(Long version, Integer serieId, Integer clienteId, Integer obraId,
                                     LocalDate fecha, LocalDate validez, List<EntradaLinea> lineas) {}
    public record PresupuestoFicha(int id, long version, String numero, String estado, boolean gestionado,
                                   String subtotal, String descuento, String impuestos, String total,
                                   PresupuestoEntrada datos) {}
    public enum EstadoPresupuesto { ACEPTADO, RECHAZADO, ANULADA }
    public record CambioPresupuesto(Long version, EstadoPresupuesto estado) {}
}
