package com.empresa.api.tesoreria;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.time.*;
import java.util.List;

public final class TesoreriaDtos {
    private TesoreriaDtos() {}
    public record Cuota(LocalDate fecha,String importe) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record Plan(List<Cuota> cuotas) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record Reparto(Integer vencimientoId,String importe) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record NuevoMovimiento(String clave,LocalDate fecha,String importe,String metodo,List<Reparto> aplicaciones) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record Conciliar(String referencia) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record Revertir(LocalDate fecha,String motivo) {
        @JsonAnySetter public void desconocido(String campo,Object valor) { throw new IllegalArgumentException("Campo no admitido"); }
    }
    public record Vencimiento(int id,int documentoId,int facturaId,int terceroId,int posicion,LocalDate fecha,String importe,String aplicado,String pendiente) {}
    public record Aplicacion(int vencimientoId,String importe,int movimientoEconomicoId,Integer reversionEconomicaId) {}
    public record Resumen(int id,String tipo,int terceroId,LocalDate fecha,String importe,String metodo,String estado) {}
    public record Movimiento(int id,String tipo,int terceroId,String clave,LocalDate fecha,String importe,String metodo,String estado,
                             String referencia,Instant conciliado,String motivo,LocalDate fechaReversion,int autorId,Instant creado,List<Aplicacion> aplicaciones) {}
}
