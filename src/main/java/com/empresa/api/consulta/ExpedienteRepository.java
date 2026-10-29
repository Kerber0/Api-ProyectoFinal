package com.empresa.api.consulta;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.empresa.api.consulta.ExpedienteDtos.*;
import com.empresa.api.common.Pagina;

@Repository
public class ExpedienteRepository {
    private final JdbcTemplate jdbc;
    public ExpedienteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String PRESUPUESTOS = """
            FROM Presupuestos p JOIN API_Presupuesto_Empresa pe ON pe.id_presupuesto = p.id_presupuesto
            JOIN API_Cliente_Empresa ce ON ce.id_cliente = p.id_cliente AND ce.id_empresa = pe.id_empresa
            JOIN Estados_Factura s ON s.id_estado = p.id_estado
            LEFT JOIN Obras o ON o.id_obra = p.id_obra
            LEFT JOIN API_Obra_Empresa oe ON oe.id_obra = o.id_obra
            """;
    private static final String AMBITO_PRESUPUESTO = """
            WHERE pe.id_empresa = ? AND (p.id_obra IS NULL OR
                  (oe.id_empresa = pe.id_empresa AND o.id_cliente = p.id_cliente))
            """;
    private static final String DOCUMENTOS = """
            FROM Documentos d JOIN Obras o ON o.id_obra = d.id_obra
            JOIN API_Obra_Empresa oe ON oe.id_obra = o.id_obra
            JOIN API_Cliente_Empresa ce ON ce.id_cliente = o.id_cliente AND ce.id_empresa = oe.id_empresa
            """;
    private static final String AMBITO_DOCUMENTO = "WHERE oe.id_empresa = ? AND d.id_obra = ? ";
    private static final String DOCUMENTO_COLUMNAS = "d.id_documento, d.id_obra, d.nombre, d.tipo, d.fecha_subida";

    Pagina<PresupuestoResumen> presupuestos(int empresa, ConsultaParametros p) {
        String from = PRESUPUESTOS + AMBITO_PRESUPUESTO;
        var args = new ArrayList<Object>(List.of(empresa));
        if (!p.q().isEmpty()) { from += " AND p.numero LIKE ? ESCAPE '!'"; args.add(p.patron()); }
        if (!p.estado().isEmpty()) { from += " AND s.descripcion = ?"; args.add(p.estado()); }
        if (p.cliente() != null) { from += " AND p.id_cliente = ?"; args.add(p.cliente()); }
        if (p.obra() != null) { from += " AND p.id_obra = ?"; args.add(p.obra()); }
        return pagina("p.id_presupuesto, p.numero, p.fecha, s.descripcion, p.id_cliente, p.id_obra, p.total",
                from, args, (rs, row) -> new PresupuestoResumen(rs.getInt(1), rs.getString(2),
                        rs.getObject(3, LocalDate.class), rs.getString(4), rs.getInt(5),
                        rs.getObject(6, Integer.class), rs.getBigDecimal(7).toPlainString()), p);
    }

    Optional<PresupuestoDetalle> presupuesto(int empresa, int id) {
        return jdbc.query("""
                SELECT p.id_presupuesto, p.numero, p.fecha, p.validez, s.descripcion, p.id_cliente, p.id_obra,
                       p.subtotal, p.descuento, p.impuestos, p.total
                """ + PRESUPUESTOS + AMBITO_PRESUPUESTO + " AND p.id_presupuesto = ?",
                (rs, row) -> new PresupuestoDetalle(rs.getInt(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                        rs.getObject(4, LocalDate.class), rs.getString(5), rs.getInt(6), rs.getObject(7, Integer.class),
                        rs.getBigDecimal(8).toPlainString(), rs.getBigDecimal(9).toPlainString(),
                        rs.getBigDecimal(10).toPlainString(), rs.getBigDecimal(11).toPlainString(), "PLANO"), empresa, id)
                .stream().findFirst();
    }

    private static final String LINEA_COLUMNAS = "l.id_linea, l.id_presupuesto, l.descripcion, l.cantidad, l.precio, l.descuento, l.iva, l.importe";
    private static final String LINEAS = PRESUPUESTOS + " JOIN Lineas_Presupuesto l ON l.id_presupuesto = p.id_presupuesto ";
    Pagina<Linea> lineas(int empresa, int presupuesto, ConsultaParametros p) {
        return pagina(LINEA_COLUMNAS, LINEAS + AMBITO_PRESUPUESTO + " AND p.id_presupuesto = ?",
                List.of(empresa, presupuesto), (rs, row) -> linea(rs), p);
    }
    Optional<Linea> linea(int empresa, int presupuesto, int id) {
        return jdbc.query("SELECT " + LINEA_COLUMNAS + " " + LINEAS + AMBITO_PRESUPUESTO +
                        " AND p.id_presupuesto = ? AND l.id_linea = ?", (rs, row) -> linea(rs), empresa, presupuesto, id)
                .stream().findFirst();
    }
    private static Linea linea(ResultSet rs) throws SQLException {
        return new Linea(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getBigDecimal(4).toPlainString(),
                rs.getBigDecimal(5).toPlainString(), rs.getBigDecimal(6).toPlainString(),
                rs.getBigDecimal(7).toPlainString(), rs.getBigDecimal(8).toPlainString());
    }

    Pagina<Documento> documentos(int empresa, int obra, ConsultaParametros p) {
        String from = DOCUMENTOS + AMBITO_DOCUMENTO;
        var args = new ArrayList<Object>(List.of(empresa, obra));
        if (!p.q().isEmpty()) { from += " AND d.nombre LIKE ? ESCAPE '!'"; args.add(p.patron()); }
        return pagina(DOCUMENTO_COLUMNAS, from, args, (rs, row) -> documento(rs), p);
    }
    Optional<Documento> documento(int empresa, int obra, int id) {
        return jdbc.query("SELECT " + DOCUMENTO_COLUMNAS + " " + DOCUMENTOS + AMBITO_DOCUMENTO + " AND d.id_documento = ?",
                (rs, row) -> documento(rs), empresa, obra, id).stream().findFirst();
    }
    Optional<String> claveArchivo(int empresa, int obra, int documento) {
        return jdbc.query("SELECT a.clave " + DOCUMENTOS + """
                JOIN API_Archivo_Documento a ON a.id_documento = d.id_documento AND a.id_empresa = oe.id_empresa
                """ + AMBITO_DOCUMENTO + " AND d.id_documento = ?", (rs, row) -> rs.getString(1), empresa, obra, documento)
                .stream().findFirst();
    }
    private static Documento documento(ResultSet rs) throws SQLException {
        return new Documento(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getObject(5, LocalDateTime.class));
    }

    private <T> Pagina<T> pagina(String select, String from, List<Object> args, RowMapper<T> mapper, ConsultaParametros p) {
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
        var pageArgs = new ArrayList<>(args);
        pageArgs.add(p.size()); pageArgs.add(p.offset());
        var items = jdbc.query("SELECT " + select + " " + from + " ORDER BY " + p.orderBy() + " LIMIT ? OFFSET ?", mapper, pageArgs.toArray());
        return new Pagina<>(items, p.page(), p.size(), total, (total + p.size() - 1) / p.size(), p.sort());
    }
}
