package com.empresa.api.consulta;

import java.time.LocalDate;
import com.empresa.api.consulta.InformeDtos;

public final class DashboardDtos {
    private DashboardDtos() {}
    /** Los importes se transmiten como texto decimal exacto, siguiendo el contrato del informe existente. */
    public record Resumen(int empresaId, LocalDate desde, LocalDate hasta,
                          InformeDtos.Facturacion facturacionClientes,
                          InformeDtos.Facturacion facturacionProveedores,
                          InformeDtos.Movimientos cobrosRegistrados,
                          InformeDtos.Movimientos pagosRegistrados,
                          long obrasTotales, long presupuestosTotales) {}
}
