package com.empresa.api.consulta;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.empresa.api.consulta.StockDtos.*;
import com.empresa.api.common.Pagina;

@Repository
public class StockRepository {
    private final JdbcTemplate jdbc;
    public StockRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String ARTICULOS = """
            FROM Articulos a JOIN API_Articulo_Empresa ae ON ae.referencia_articulo = a.referencia
            LEFT JOIN Unidades_Medida u ON u.id_unidad = a.id_unidad
            WHERE ae.id_empresa = ?
            """;
    private static final String ARTICULO_COLUMNAS = "a.referencia, a.descripcion, a.marca, a.modelo, u.abreviatura, a.activo";
    private static final String ALMACENES = """
            FROM Almacenes al JOIN API_Almacen_Empresa ale ON ale.id_almacen = al.id_almacen
            WHERE ale.id_empresa = ?
            """;
    private static final String ALMACEN_COLUMNAS = "al.id_almacen, al.codigo_almacen, al.descripcion, al.activo";

    // La pertenencia de articulo y almacen de origen debe coincidir. El destino
    // solo se revela si pertenece a la misma empresa. No se usan textos libres.
    private static final String MOVIMIENTOS = """
            WITH base AS (
                SELECT ms.*, ae.id_empresa,
                    CASE WHEN ms.tipo_movimiento = 'TRASPASO' THEN destino.id_almacen END AS destino_id
                FROM Movimientos_Stock ms
                JOIN API_Articulo_Empresa ae ON ae.referencia_articulo = ms.referencia_articulo
                JOIN API_Almacen_Empresa origen ON origen.id_almacen = ms.id_almacen AND origen.id_empresa = ae.id_empresa
                LEFT JOIN API_Traspaso_Stock t ON t.id_movimiento = ms.id_movimiento
                LEFT JOIN API_Almacen_Empresa destino ON destino.id_almacen = t.id_almacen_destino
                    AND destino.id_empresa = ae.id_empresa AND destino.id_almacen <> ms.id_almacen
                WHERE ae.id_empresa = ?
            ), movimientos AS (
                SELECT base.*,
                    CASE
                        WHEN tipo_movimiento = 'ENTRADA' AND cantidad >= 0 THEN cantidad
                        WHEN tipo_movimiento = 'SALIDA' AND cantidad >= 0 THEN -cantidad
                        WHEN tipo_movimiento = 'AJUSTE' THEN cantidad
                        WHEN tipo_movimiento = 'TRASPASO' AND cantidad >= 0 AND destino_id IS NOT NULL THEN -cantidad
                        ELSE NULL
                    END AS efecto
                FROM base
            )
            """;
    private static final String MOVIMIENTO_COLUMNAS = "m.id_movimiento, m.referencia_articulo, m.id_almacen, m.destino_id, m.fecha, m.tipo_movimiento, m.cantidad, m.efecto";
    private static final String EFECTOS = """
            , efectos AS (
                SELECT referencia_articulo, id_almacen, efecto FROM movimientos
                UNION ALL
                SELECT referencia_articulo, destino_id, -efecto FROM movimientos WHERE destino_id IS NOT NULL
                UNION ALL
                -- Un destino desconocido deja incierto el saldo de ese articulo
                -- en los demas almacenes de la empresa, no solo en el origen.
                SELECT m.referencia_articulo, al.id_almacen, NULL FROM movimientos m
                JOIN API_Almacen_Empresa al ON al.id_empresa = m.id_empresa AND al.id_almacen <> m.id_almacen
                WHERE m.tipo_movimiento = 'TRASPASO' AND m.destino_id IS NULL
            )
            """;

    Pagina<Articulo> articulos(int empresa, ConsultaParametros p) {
        var args = new ArrayList<Object>(List.of(empresa));
        String from = ARTICULOS + buscarArticulo(p, args);
        return pagina("", List.of(), ARTICULO_COLUMNAS, from, args, (rs, row) -> articulo(rs), p);
    }
    Optional<Articulo> articulo(int empresa, String referencia) {
        return jdbc.query("SELECT " + ARTICULO_COLUMNAS + " " + ARTICULOS + " AND a.referencia = ?",
                (rs, row) -> articulo(rs), empresa, referencia).stream().findFirst();
    }
    Pagina<Almacen> almacenes(int empresa, ConsultaParametros p) {
        String from = ALMACENES;
        var args = new ArrayList<Object>(List.of(empresa));
        if (!p.q().isEmpty()) {
            from += " AND (al.codigo_almacen LIKE ? ESCAPE '!' OR al.descripcion LIKE ? ESCAPE '!')";
            args.add(p.patron()); args.add(p.patron());
        }
        return pagina("", List.of(), ALMACEN_COLUMNAS, from, args, (rs, row) -> almacen(rs), p);
    }
    Optional<Almacen> almacen(int empresa, int id) {
        return jdbc.query("SELECT " + ALMACEN_COLUMNAS + " " + ALMACENES + " AND al.id_almacen = ?",
                (rs, row) -> almacen(rs), empresa, id).stream().findFirst();
    }
    Pagina<Existencia> stock(int empresa, int almacen, ConsultaParametros p) {
        String from = """
                FROM Articulos a JOIN API_Articulo_Empresa ae ON ae.referencia_articulo = a.referencia
                LEFT JOIN (SELECT referencia_articulo, SUM(efecto) AS cantidad,
                    SUM(CASE WHEN efecto IS NULL THEN 1 ELSE 0 END) AS pendientes
                    FROM efectos WHERE id_almacen = ? GROUP BY referencia_articulo) saldo
                    ON saldo.referencia_articulo = a.referencia
                WHERE ae.id_empresa = ?
                """;
        var args = new ArrayList<Object>(List.of(almacen, empresa));
        from += buscarArticulo(p, args);
        return pagina(MOVIMIENTOS + EFECTOS, List.of(empresa),
                "a.referencia, COALESCE(saldo.cantidad, 0), COALESCE(saldo.pendientes, 0)", from, args,
                (rs, row) -> new Existencia(rs.getString(1), almacen,
                        rs.getLong(3) == 0 ? decimal(rs.getBigDecimal(2)) : null,
                        rs.getLong(3) == 0, rs.getLong(3), "DEMO_SUMA_MOVIMIENTOS_POR_ALMACEN"), p);
    }
    Pagina<Movimiento> movimientos(int empresa, Integer almacen, ConsultaParametros p) {
        String from = "FROM movimientos m WHERE 1 = 1";
        var args = new ArrayList<Object>();
        if (almacen != null) {
            from += " AND (m.id_almacen = ? OR m.destino_id = ?)";
            args.add(almacen); args.add(almacen);
        }
        if (p.desde() != null) { from += " AND m.fecha >= ?"; args.add(p.desde().atStartOfDay()); }
        // DATETIME de la DEMO tiene precision de segundos; ambos dias son inclusivos.
        if (p.hasta() != null) { from += " AND m.fecha <= ?"; args.add(p.hasta().atTime(23, 59, 59)); }
        return pagina(MOVIMIENTOS, List.of(empresa), MOVIMIENTO_COLUMNAS, from, args,
                (rs, row) -> movimiento(rs), p);
    }
    Optional<Movimiento> movimiento(int empresa, int id) {
        return jdbc.query(MOVIMIENTOS + " SELECT " + MOVIMIENTO_COLUMNAS + " FROM movimientos m WHERE m.id_movimiento = ?",
                (rs, row) -> movimiento(rs), empresa, id).stream().findFirst();
    }
    private static String buscarArticulo(ConsultaParametros p, List<Object> args) {
        if (p.q().isEmpty()) { return ""; }
        args.add(p.patron()); args.add(p.patron());
        return " AND (a.referencia LIKE ? ESCAPE '!' OR a.descripcion LIKE ? ESCAPE '!')";
    }
    private static Articulo articulo(ResultSet rs) throws SQLException {
        return new Articulo(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6));
    }
    private static Almacen almacen(ResultSet rs) throws SQLException {
        return new Almacen(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4));
    }
    private static Movimiento movimiento(ResultSet rs) throws SQLException {
        return new Movimiento(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getObject(4, Integer.class),
                rs.getObject(5, LocalDateTime.class), rs.getString(6), decimal(rs.getBigDecimal(7)),
                decimal(rs.getBigDecimal(8)), rs.getBigDecimal(8) != null);
    }
    private static String decimal(BigDecimal value) { return value == null ? null : value.setScale(2).toPlainString(); }
    private <T> Pagina<T> pagina(String cte, List<Object> cteArgs, String select, String from,
                                List<Object> args, RowMapper<T> mapper, ConsultaParametros p) {
        var allArgs = new ArrayList<>(cteArgs);
        allArgs.addAll(args);
        long total = jdbc.queryForObject(cte + " SELECT COUNT(*) " + from, Long.class, allArgs.toArray());
        allArgs.add(p.size()); allArgs.add(p.offset());
        var items = jdbc.query(cte + " SELECT " + select + " " + from + " ORDER BY " + p.orderBy() + " LIMIT ? OFFSET ?",
                mapper, allArgs.toArray());
        return new Pagina<>(items, p.page(), p.size(), total, (total + p.size() - 1) / p.size(), p.sort());
    }
}
