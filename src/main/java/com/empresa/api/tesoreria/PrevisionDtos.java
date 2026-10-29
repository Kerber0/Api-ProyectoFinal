package com.empresa.api.tesoreria;

import java.time.LocalDate;
import java.util.List;

public final class PrevisionDtos {
    private PrevisionDtos() {}
    public record VencimientoPendiente(int id, int documentoId, int facturaId, int terceroId,
                                      String tipo, int posicion, LocalDate fecha,
                                      String importe, String aplicado, String saldoFirmado,
                                      String estadoTemporal) {}
    public record Prevision(int empresaId, LocalDate desde, LocalDate hasta, String tipo,
                           long totalRegistros, String saldoFirmadoTotal,
                           String porCobrarFirmado, String porPagarFirmado,
                           int page, int size, List<VencimientoPendiente> items) {}
}
