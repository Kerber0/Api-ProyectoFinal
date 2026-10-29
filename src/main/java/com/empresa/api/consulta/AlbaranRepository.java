package com.empresa.api.consulta;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.empresa.api.consulta.AlbaranDtos.*;
import com.empresa.api.common.Pagina;

@Repository
public class AlbaranRepository {
    private final JdbcTemplate jdbc;
    public AlbaranRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String NOMBRE = "COALESCE(NULLIF(e.razon_social, ''), NULLIF(TRIM(CONCAT_WS(' ', e.nombre, e.apellidos)), ''))";

    private static String joins(AlbaranTipo tipo) {
        return " FROM " + tipo.tabla + " a JOIN " + tipo.pertenencia + " ae ON ae.id_albaran = a.id_albaran"
                + " JOIN " + tipo.terceros + " t ON t." + tipo.terceroId + " = a." + tipo.terceroId
                + " JOIN " + tipo.pertenenciaTercero + " te ON te." + tipo.terceroId + " = t." + tipo.terceroId + " AND te.id_empresa = ae.id_empresa"
                + " JOIN Entidades e ON e.id_entidad = t.id_entidad JOIN Estados_Factura s ON s.id_estado = a.id_estado";
    }
    private static String ambito(AlbaranTipo tipo) {
        String sql = " WHERE ae.id_empresa = ? AND NOT EXISTS (SELECT 1 FROM " + tipo.lineas + " la"
                + " LEFT JOIN API_Articulo_Empresa art ON art.referencia_articulo = la.referencia_articulo AND art.id_empresa = ae.id_empresa"
                + " WHERE la.id_albaran = a.id_albaran AND la.referencia_articulo IS NOT NULL AND art.referencia_articulo IS NULL)";
        // Reutiliza el ambito de factura: misma empresa, tercero y obra coherente si existe.
        var factura = tipo.economia();
        return sql + " AND (a.id_factura IS NULL OR EXISTS (SELECT 1"
                + EconomiaRepository.joins(factura)
                + EconomiaRepository.ambito(factura).replace("fe.id_empresa = ?", "fe.id_empresa = ae.id_empresa")
                + " AND f.id_factura = a.id_factura AND f." + tipo.terceroId + " = a." + tipo.terceroId + "))";
    }
    private static String columnas(AlbaranTipo tipo) {
        return "a.id_albaran, a.numero, a.fecha, s.descripcion, t." + tipo.terceroId + ", t." + tipo.terceroCodigo
                + ", " + NOMBRE + ", " + "a.id_factura"
                + ", a.subtotal, a.descuento, a.impuestos, a.total";
    }
    Pagina<Albaran> albaranes(int empresa, AlbaranTipo tipo, Integer relacion, AlbaranParametros p) {
        String from = joins(tipo) + ambito(tipo);
        var args = new ArrayList<Object>(List.of(empresa));
        Integer filtro = relacion != null ? relacion : p.relacion();
        if (filtro != null) { from += " AND a.id_" + tipo.filtroRelacion + " = ?"; args.add(filtro); }
        if (p.tercero() != null) { from += " AND a." + tipo.terceroId + " = ?"; args.add(p.tercero()); }
        var pagina = p.pagina();
        if (!pagina.q().isEmpty()) { from += " AND a.numero LIKE ? ESCAPE '!'"; args.add(pagina.patron()); }
        if (!pagina.estado().isEmpty()) { from += " AND s.descripcion = ?"; args.add(pagina.estado()); }
        if (pagina.desde() != null) { from += " AND a.fecha >= ?"; args.add(pagina.desde()); }
        if (pagina.hasta() != null) { from += " AND a.fecha <= ?"; args.add(pagina.hasta()); }
        return pagina(columnas(tipo), from, args, (rs, row) -> albaran(rs, tipo), pagina);
    }
    Optional<Albaran> albaran(int empresa, AlbaranTipo tipo, int id) {
        return jdbc.query("SELECT " + columnas(tipo) + joins(tipo) + ambito(tipo) + " AND a.id_albaran = ?",
                (rs, row) -> albaran(rs, tipo), empresa, id).stream().findFirst();
    }
    private static final String LINEAS = "l.id_linea, l.id_albaran, l.referencia_articulo, l.descripcion, l.cantidad, l.precio, l.descuento, l.iva, l.importe";
    private static String lineasFrom(AlbaranTipo tipo) {
        return joins(tipo) + " JOIN " + tipo.lineas + " l ON l.id_albaran = a.id_albaran" + ambito(tipo) + " AND a.id_albaran = ?";
    }
    Pagina<Linea> lineas(int empresa, AlbaranTipo tipo, int albaran, ConsultaParametros p) {
        return pagina(LINEAS, lineasFrom(tipo), List.of(empresa, albaran), (rs, row) -> linea(rs, tipo), p);
    }
    Optional<Linea> linea(int empresa, AlbaranTipo tipo, int albaran, int id) {
        return jdbc.query("SELECT " + LINEAS + lineasFrom(tipo) + " AND l.id_linea = ?",
                (rs, row) -> linea(rs, tipo), empresa, albaran, id).stream().findFirst();
    }
    private static Albaran albaran(ResultSet rs, AlbaranTipo tipo) throws SQLException {
        return new Albaran(rs.getInt(1), tipo.name(), rs.getString(2), rs.getObject(3, LocalDate.class), rs.getString(4),
                new Tercero(rs.getInt(5), rs.getString(6), rs.getString(7)), rs.getObject(8, Integer.class),
                decimal(rs, 9), decimal(rs, 10), decimal(rs, 11), decimal(rs, 12));
    }
    private static Linea linea(ResultSet rs, AlbaranTipo tipo) throws SQLException {
        return new Linea(rs.getInt(1), rs.getInt(2), tipo.name(), rs.getString(3), rs.getString(4),
                decimal(rs, 5), decimal(rs, 6), decimal(rs, 7), decimal(rs, 8), decimal(rs, 9));
    }
    private static String decimal(ResultSet rs, int index) throws SQLException {
        var value = rs.getBigDecimal(index);
        return value == null ? null : value.toPlainString();
    }
    private <T> Pagina<T> pagina(String select, String from, List<Object> args, RowMapper<T> mapper, ConsultaParametros p) {
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
        var pageArgs = new ArrayList<>(args);
        pageArgs.add(p.size()); pageArgs.add(p.offset());
        var items = jdbc.query("SELECT " + select + from + " ORDER BY " + p.orderBy() + " LIMIT ? OFFSET ?", mapper, pageArgs.toArray());
        return new Pagina<>(items, p.page(), p.size(), total, (total + p.size() - 1) / p.size(), p.sort());
    }
}
