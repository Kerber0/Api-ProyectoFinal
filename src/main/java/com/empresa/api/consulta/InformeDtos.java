package com.empresa.api.consulta;

import java.time.LocalDate;
import java.util.List;

public final class InformeDtos {
    private InformeDtos() {}
    public record Facturacion(long facturas, String subtotal, String descuento, String impuestos, String total) {}
    public record Movimientos(long movimientos, String importe) {}
    public record Saldos(long facturas, String totalFacturas, String movimientosRegistrados, String saldoAritmetico) {}
    public record Mes<T>(String mes, T valores) {}
    public record Informe<T>(int empresaId, String tipo, LocalDate desde, LocalDate hasta,
                             String criterio, List<Mes<T>> meses, T totales) {}
    public record Estado(long id, String estado, String color, int orden, long cantidad) {}
    public record InformeEstados(int empresaId, String tipo, LocalDate desde, LocalDate hasta,
                                 String criterio, long total, List<Estado> estados) {}
    public record TotalesEconomicos(Facturacion facturacionClientes, Facturacion facturacionProveedores,
                                    Movimientos cobrosRegistrados, Movimientos pagosRegistrados,
                                    Saldos pendienteCobro, Saldos pendientePago,
                                    String previsionEntradas, String previsionSalidas,
                                    String flujoCajaRegistrado, String flujoCajaPrevisto) {}
    public record EmpresaEconomica(int empresaId, String empresaNombre, TotalesEconomicos valores) {}
    public record InformeGrupo(LocalDate desde, LocalDate hasta, String criterio,
                               List<EmpresaEconomica> empresas, TotalesEconomicos totales) {}
}
