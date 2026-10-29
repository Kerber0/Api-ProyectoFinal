package com.empresa.api.consulta;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.empresa.api.consulta.EconomiaDtos.*;
import com.empresa.api.common.Pagina;

@Repository
public class EconomiaRepository {
    private final JdbcTemplate jdbc;
    public EconomiaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String REGLA = "DEMO_TOTAL_MENOS_MOVIMIENTOS";
    private static final String NOMBRE = "COALESCE(NULLIF(e.razon_social, ''), NULLIF(TRIM(CONCAT_WS(' ', e.nombre, e.apellidos)), ''))";

    static String joins(EconomiaTipo tipo) {
        return " FROM " + tipo.facturas + " f JOIN " + tipo.pertenencia + " fe ON fe.id_factura = f.id_factura"
                + " JOIN " + tipo.terceros + " t ON t." + tipo.terceroId + " = f." + tipo.terceroId
                + " JOIN " + tipo.pertenenciaTercero + " te ON te." + tipo.terceroId + " = t." + tipo.terceroId + " AND te.id_empresa = fe.id_empresa"
                + " JOIN Entidades e ON e.id_entidad = t.id_entidad JOIN Estados_Factura s ON s.id_estado = f.id_estado"
                + (tipo == EconomiaTipo.CLIENTE ? " LEFT JOIN Obras o ON o.id_obra = f.id_obra LEFT JOIN API_Obra_Empresa oe ON oe.id_obra = o.id_obra" : "");
    }
    static String ambito(EconomiaTipo tipo) {
        return " WHERE fe.id_empresa = ?" + (tipo == EconomiaTipo.CLIENTE
                ? " AND (f.id_obra IS NULL OR (oe.id_empresa = fe.id_empresa AND o.id_cliente = f.id_cliente))" : "");
    }
    private static String columnasFactura(EconomiaTipo tipo) {
        return "f.id_factura, f.numero, f.fecha, f.fecha_vencimiento, s.descripcion, t." + tipo.terceroId
                + ", t." + tipo.terceroCodigo + ", " + NOMBRE + ", "
                + (tipo == EconomiaTipo.CLIENTE ? "f.id_obra" : "CAST(NULL AS INTEGER)")
                + ", f.subtotal, f.descuento, f.impuestos, f.total";
    }
    private static String filtros(EconomiaTipo tipo, EconomiaParametros p, List<Object> args) {
        String sql = "";
        if (p.tercero() != null) { sql += " AND f." + tipo.terceroId + " = ?"; args.add(p.tercero()); }
        if (p.obra() != null) { sql += " AND f.id_obra = ?"; args.add(p.obra()); }
        if (p.factura() != null) { sql += " AND f.id_factura = ?"; args.add(p.factura()); }
        return sql;
    }
    private static String fechas(String columna, ConsultaParametros p, List<Object> args) {
        String sql = "";
        if (p.desde() != null) { sql += " AND " + columna + " >= ?"; args.add(p.desde()); }
        if (p.hasta() != null) { sql += " AND " + columna + " <= ?"; args.add(p.hasta()); }
        return sql;
    }

    Pagina<Factura> facturas(int empresa, EconomiaTipo tipo, EconomiaParametros p) {
        var args = new ArrayList<Object>(List.of(empresa));
        String from = joins(tipo) + ambito(tipo) + filtros(tipo, p, args) + fechas("f.fecha", p.pagina(), args);
        if (!p.pagina().q().isEmpty()) { from += " AND f.numero LIKE ? ESCAPE '!'"; args.add(p.pagina().patron()); }
        if (!p.pagina().estado().isEmpty()) { from += " AND s.descripcion = ?"; args.add(p.pagina().estado()); }
        return pagina(columnasFactura(tipo), from, args, (rs, row) -> factura(rs, tipo, empresa), p.pagina());
    }
    Optional<Factura> factura(int empresa, EconomiaTipo tipo, int id) {
        return jdbc.query("SELECT " + columnasFactura(tipo) + joins(tipo) + ambito(tipo) + " AND f.id_factura = ?",
                (rs, row) -> factura(rs, tipo, empresa), empresa, id).stream().findFirst();
    }
    private static Factura factura(ResultSet rs, EconomiaTipo tipo, int empresa) throws SQLException {
        return new Factura(rs.getInt(1), empresa, tipo.name(), rs.getString(2), rs.getObject(3, LocalDate.class),
                rs.getObject(4, LocalDate.class), rs.getString(5), new Tercero(rs.getInt(6), rs.getString(7), rs.getString(8)),
                rs.getObject(9, Integer.class), decimal(rs, 10), decimal(rs, 11), decimal(rs, 12), decimal(rs, 13));
    }

    private static final String LINEAS = "l.id_linea, l.id_factura, l.descripcion, l.cantidad, l.precio, l.descuento, l.iva, l.importe";
    private static String lineasFrom(EconomiaTipo tipo) {
        return joins(tipo) + " JOIN " + tipo.lineas + " l ON l.id_factura = f.id_factura" + ambito(tipo) + " AND f.id_factura = ?";
    }
    Pagina<Linea> lineas(int empresa, EconomiaTipo tipo, int factura, ConsultaParametros p) {
        return pagina(LINEAS, lineasFrom(tipo), List.of(empresa, factura), (rs, row) -> linea(rs), p);
    }
    Optional<Linea> linea(int empresa, EconomiaTipo tipo, int factura, int id) {
        return jdbc.query("SELECT " + LINEAS + lineasFrom(tipo) + " AND l.id_linea = ?",
                (rs, row) -> linea(rs), empresa, factura, id).stream().findFirst();
    }
    private static Linea linea(ResultSet rs) throws SQLException {
        return new Linea(rs.getInt(1), rs.getInt(2), rs.getString(3), decimal(rs, 4), decimal(rs, 5),
                decimal(rs, 6), decimal(rs, 7), decimal(rs, 8));
    }
    private static String decimal(ResultSet rs, int index) throws SQLException {
        BigDecimal value = rs.getBigDecimal(index);
        return value == null ? null : value.toPlainString();
    }

    static String movimientosFrom(EconomiaTipo tipo) {
        return joins(tipo) + " JOIN " + tipo.movimientos + " m ON m.id_factura = f.id_factura" + ambito(tipo);
    }
    private static String columnasMovimiento(EconomiaTipo tipo) {
        return "m." + tipo.movimientoId + " AS movimiento_id, m.id_factura, m.fecha, m.importe, m.metodo_pago";
    }
    Pagina<Movimiento> movimientos(int empresa, EconomiaTipo tipo, Integer factura, EconomiaParametros p) {
        var args = new ArrayList<Object>(List.of(empresa));
        String from = movimientosFrom(tipo) + filtros(tipo, p, args) + fechas("m.fecha", p.pagina(), args);
        if (factura != null) { from += " AND f.id_factura = ?"; args.add(factura); }
        return pagina(columnasMovimiento(tipo), from, args, (rs, row) -> movimiento(rs), p.pagina());
    }
    Optional<Movimiento> movimiento(int empresa, EconomiaTipo tipo, int id) {
        return jdbc.query("SELECT " + columnasMovimiento(tipo) + movimientosFrom(tipo) + " AND m." + tipo.movimientoId + " = ?",
                (rs, row) -> movimiento(rs), empresa, id).stream().findFirst();
    }
    private static Movimiento movimiento(ResultSet rs) throws SQLException {
        return new Movimiento(rs.getInt(1), rs.getInt(2), rs.getObject(3, LocalDate.class), decimal(rs, 4), rs.getString(5));
    }

    // Agregar movimientos antes de unir evita multiplicarlos por las lineas.
    // Se suman todos los registros, sin deducir anulaciones ni limitar por fechas de filtro.
    static String acumulado(EconomiaTipo tipo) {
        return " LEFT JOIN (SELECT id_factura, SUM(importe) AS registrado FROM " + tipo.movimientos
                + " GROUP BY id_factura) a ON a.id_factura = f.id_factura";
    }
    Optional<Saldo> saldo(int empresa, EconomiaTipo tipo, int id) {
        return jdbc.query("SELECT f.id_factura, f.total, COALESCE(a.registrado, 0.00)" + joins(tipo) + acumulado(tipo)
                        + ambito(tipo) + " AND f.id_factura = ?", (rs, row) -> {
                    BigDecimal total = rs.getBigDecimal(2), registrado = rs.getBigDecimal(3);
                    return new Saldo(rs.getInt(1), tipo.name(), total.toPlainString(), registrado.toPlainString(),
                            total.subtract(registrado).toPlainString(), REGLA);
                }, empresa, id).stream().findFirst();
    }
    Pagina<Vencimiento> vencimientos(int empresa, EconomiaTipo tipo, EconomiaParametros p) {
        var args = new ArrayList<Object>(List.of(empresa));
        String from = joins(tipo) + acumulado(tipo) + ambito(tipo) + " AND f.fecha_vencimiento IS NOT NULL"
                + filtros(tipo, p, args) + fechas("f.fecha_vencimiento", p.pagina(), args);
        return pagina("f.id_factura, f.numero, f.fecha_vencimiento, f.total, COALESCE(a.registrado, 0.00)", from, args, (rs, row) -> {
            BigDecimal total = rs.getBigDecimal(4), registrado = rs.getBigDecimal(5);
            return new Vencimiento(rs.getInt(1), tipo.name(), rs.getString(2), rs.getObject(3, LocalDate.class),
                    total.toPlainString(), registrado.toPlainString(), total.subtract(registrado).toPlainString(), REGLA);
        }, p.pagina());
    }

    private <T> Pagina<T> pagina(String select, String from, List<Object> args, RowMapper<T> mapper, ConsultaParametros p) {
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
        var pageArgs = new ArrayList<>(args);
        pageArgs.add(p.size()); pageArgs.add(p.offset());
        var items = jdbc.query("SELECT " + select + from + " ORDER BY " + p.orderBy() + " LIMIT ? OFFSET ?", mapper, pageArgs.toArray());
        return new Pagina<>(items, p.page(), p.size(), total, (total + p.size() - 1) / p.size(), p.sort());
    }
}
