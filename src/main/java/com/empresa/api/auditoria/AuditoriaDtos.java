package com.empresa.api.auditoria;

import java.time.Instant;
import tools.jackson.databind.JsonNode;

public final class AuditoriaDtos {
    private AuditoriaDtos() {}
    public enum Modulo { MAESTROS, USUARIOS, LABORAL, OBSERVACIONES, FACTURACION, TESORERIA, DOCUMENTOS, GESTION }
    public record Evento(long id, Modulo modulo, long origenId, int autorId, Integer empresaId,
                         String entidad, int entidadId, String accion, Instant fecha,
                         boolean historico, JsonNode cambios) {}
}
