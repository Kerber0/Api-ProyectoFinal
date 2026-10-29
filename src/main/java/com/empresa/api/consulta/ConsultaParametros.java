package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import org.springframework.util.MultiValueMap;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

record ConsultaParametros(int page, int size, String sort, String orderBy, String q, String estado, Integer cliente, Integer obra,
                          Integer operario, LocalDate desde, LocalDate hasta) {
    enum Tipo { CLIENTES, OBRAS, CONTACTOS, DIRECCIONES, CONTACTOS_OBRA, PRESUPUESTOS, LINEAS, DOCUMENTOS,
        OPERARIOS, PARTES, PARTES_OPERARIO, PARTES_OBRA, FACTURAS, MOVIMIENTOS, VENCIMIENTOS,
        ARTICULOS, ALMACENES, STOCK, MOVIMIENTOS_STOCK, PROVEEDORES, PEDIDOS, ALBARANES, INCIDENCIAS, EVENTOS, TAREAS }

    static ConsultaParametros parse(MultiValueMap<String, String> raw, Tipo tipo) {
        Set<String> allowed = switch (tipo) {
            case CLIENTES, PROVEEDORES -> Set.of("page", "size", "sort", "q");
            case OBRAS -> Set.of("page", "size", "sort", "q", "estado", "cliente");
            case PRESUPUESTOS -> Set.of("page", "size", "sort", "q", "estado", "cliente", "obra");
            case DOCUMENTOS -> Set.of("page", "size", "sort", "q");
            case OPERARIOS, ARTICULOS, ALMACENES, STOCK -> Set.of("page", "size", "sort", "q");
            case PARTES -> Set.of("page", "size", "sort", "operario", "obra", "desde", "hasta");
            case INCIDENCIAS -> Set.of("page", "size", "sort", "operario", "obra", "estado", "desde", "hasta");
            case EVENTOS -> Set.of("page", "size", "sort", "q", "obra", "desde", "hasta");
            case TAREAS -> Set.of("page", "size", "sort", "q", "operario", "obra", "estado", "desde", "hasta");
            case PARTES_OPERARIO -> Set.of("page", "size", "sort", "obra", "desde", "hasta");
            case PARTES_OBRA -> Set.of("page", "size", "sort", "operario", "desde", "hasta");
            case FACTURAS, PEDIDOS, ALBARANES -> Set.of("page", "size", "sort", "q", "estado", "desde", "hasta");
            case MOVIMIENTOS, VENCIMIENTOS, MOVIMIENTOS_STOCK -> Set.of("page", "size", "sort", "desde", "hasta");
            default -> Set.of("page", "size", "sort");
        };
        raw.forEach((key, values) -> {
            if (!allowed.contains(key) || values.size() != 1) { throw ApiException.invalido("query"); }
        });
        int page = entero(raw.getFirst("page"), 0, 0, 100000, "page");
        int size = entero(raw.getFirst("size"), 20, 1, 100, "size");
        Map<String, String> columns = switch (tipo) {
            case CLIENTES -> Map.of("id", "c.id_cliente", "codigo", "c.codigo_cliente", "nombre", "nombre_cliente");
            case OBRAS -> Map.of("id", "o.id_obra", "codigo", "o.codigo_obra", "titulo", "o.titulo");
            case CONTACTOS -> Map.of("id", "k.id_contacto");
            case DIRECCIONES -> Map.of("id", "d.id_direccion");
            case CONTACTOS_OBRA -> Map.of("id", "oc.id_obra_contacto");
            case PRESUPUESTOS -> Map.of("id", "p.id_presupuesto", "numero", "p.numero", "fecha", "p.fecha");
            case LINEAS -> Map.of("id", "l.id_linea");
            case DOCUMENTOS -> Map.of("id", "d.id_documento", "nombre", "d.nombre", "fecha", "d.fecha_subida");
            case OPERARIOS -> Map.of("id", "op.id_operario", "codigo", "op.codigo_operario", "nombre", "nombre_operario");
            case PARTES, PARTES_OPERARIO, PARTES_OBRA -> Map.of("id", "pm.id_parte", "fecha", "pm.fecha");
            case FACTURAS -> Map.of("id", "f.id_factura", "numero", "f.numero", "fecha", "f.fecha");
            case MOVIMIENTOS -> Map.of("id", "movimiento_id", "fecha", "m.fecha");
            case VENCIMIENTOS -> Map.of("id", "f.id_factura", "fecha", "f.fecha_vencimiento");
            case ARTICULOS -> Map.of("referencia", "a.referencia", "descripcion", "a.descripcion");
            case ALMACENES -> Map.of("id", "al.id_almacen", "codigo", "al.codigo_almacen", "descripcion", "al.descripcion");
            case STOCK -> Map.of("referencia", "a.referencia");
            case MOVIMIENTOS_STOCK -> Map.of("id", "m.id_movimiento", "fecha", "m.fecha");
            case PROVEEDORES -> Map.of("id", "pr.id_proveedor", "codigo", "pr.codigo_proveedor", "nombre", "nombre_proveedor");
            case PEDIDOS -> Map.of("id", "p.id_pedido", "numero", "p.numero", "fecha", "p.fecha");
            case ALBARANES -> Map.of("id", "a.id_albaran", "numero", "a.numero", "fecha", "a.fecha");
            case INCIDENCIAS -> Map.of("id", "i.id_incidencia", "fecha", "i.fecha");
            case EVENTOS -> Map.of("id", "x.id_evento", "titulo", "x.titulo", "fechaInicio", "x.fecha_inicio");
            case TAREAS -> Map.of("id", "x.id_tarea", "titulo", "x.titulo", "fechaLimite", "x.fecha_limite");
        };
        String key = tipo == Tipo.ARTICULOS || tipo == Tipo.STOCK ? "referencia" : "id";
        String sort = raw.getFirst("sort") == null ? key + ",asc" : raw.getFirst("sort");
        String[] parts = sort.split(",", -1);
        if (parts.length != 2 || !columns.containsKey(parts[0]) || !Set.of("asc", "desc").contains(parts[1])) {
            throw ApiException.invalido("sort");
        }
        String orderBy = columns.get(parts[0]) + " " + parts[1] + (parts[1].equals("asc") ? " NULLS FIRST" : " NULLS LAST");
        if (!parts[0].equals(key)) { orderBy += ", " + columns.get(key) + " " + parts[1]; }
        String q = texto(raw.getFirst("q"), 100, "q");
        String estado = texto(raw.getFirst("estado"), 300, "estado");
        Integer cliente = raw.getFirst("cliente") == null ? null
                : entero(raw.getFirst("cliente"), 0, 1, Integer.MAX_VALUE, "cliente");
        Integer obra = raw.getFirst("obra") == null ? null
                : entero(raw.getFirst("obra"), 0, 1, Integer.MAX_VALUE, "obra");
        Integer operario = raw.getFirst("operario") == null ? null
                : entero(raw.getFirst("operario"), 0, 1, Integer.MAX_VALUE, "operario");
        LocalDate desde = fecha(raw.getFirst("desde"), "desde");
        LocalDate hasta = fecha(raw.getFirst("hasta"), "hasta");
        if (desde != null && hasta != null && desde.isAfter(hasta)) { throw ApiException.invalido("hasta"); }
        return new ConsultaParametros(page, size, sort, orderBy, q, estado, cliente, obra, operario, desde, hasta);
    }

    static LocalDate fecha(String value, String campo) {
        if (value == null) { return null; }
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) { throw ApiException.invalido(campo); }
        try {
            LocalDate parsed = LocalDate.parse(value);
            if (parsed.getYear() < 1000) { throw ApiException.invalido(campo); }
            return parsed;
        } catch (DateTimeParseException ex) { throw ApiException.invalido(campo); }
    }

    static int entero(String value, int fallback, int min, int max, String campo) {
        if (value == null) { return fallback; }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) { throw ApiException.invalido(campo); }
            return parsed;
        } catch (NumberFormatException ex) { throw ApiException.invalido(campo); }
    }

    private static String texto(String value, int max, String campo) {
        if (value == null) { return ""; }
        if (value.length() > max) { throw ApiException.invalido(campo); }
        return value.trim();
    }

    String patron() { return "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%"; }
    long offset() { return (long) page * size; }
}
