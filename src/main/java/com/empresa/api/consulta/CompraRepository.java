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
import static com.empresa.api.consulta.CompraDtos.*;
import com.empresa.api.common.Pagina;

@Repository
public class CompraRepository {
    private final JdbcTemplate jdbc;
    public CompraRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String NOMBRE = "COALESCE(NULLIF(e.razon_social, ''), NULLIF(TRIM(CONCAT_WS(' ', e.nombre, e.apellidos)), ''))";
    private static final String PROVEEDORES = """
             FROM Proveedores pr JOIN API_Proveedor_Empresa pre ON pre.id_proveedor = pr.id_proveedor
             JOIN Entidades e ON e.id_entidad = pr.id_entidad WHERE pre.id_empresa = ?
            """;
    private static final String PROVEEDOR_COLUMNAS = "pr.id_proveedor, pr.codigo_proveedor, " + NOMBRE + " AS nombre_proveedor, pr.tipo_proveedor, e.activo";
    private static final String PEDIDOS = """
             FROM Pedidos_Proveedor p JOIN API_Pedido_Proveedor_Empresa pe ON pe.id_pedido = p.id_pedido
             JOIN Proveedores pr ON pr.id_proveedor = p.id_proveedor
             JOIN API_Proveedor_Empresa pre ON pre.id_proveedor = pr.id_proveedor AND pre.id_empresa = pe.id_empresa
             JOIN Entidades e ON e.id_entidad = pr.id_entidad
             JOIN Estados_Factura s ON s.id_estado = p.id_estado
            """;
    // Un pedido con una linea de articulo ajeno o sin pertenencia se excluye entero.
    // Evita publicar documentos parciales y mantiene el mismo ambito en todos los endpoints.
    private static final String AMBITO = """
             WHERE pe.id_empresa = ? AND NOT EXISTS (
                 SELECT 1 FROM Lineas_Pedido lp
                 LEFT JOIN API_Articulo_Empresa ae ON ae.referencia_articulo = lp.referencia_articulo
                     AND ae.id_empresa = pe.id_empresa
                 WHERE lp.id_pedido = p.id_pedido AND ae.referencia_articulo IS NULL
             )
            """;
    private static final String PEDIDO_COLUMNAS = "p.id_pedido, p.numero, p.fecha, s.descripcion, pr.id_proveedor, pr.codigo_proveedor, "
            + NOMBRE + ", p.subtotal, p.descuento, p.impuestos, p.total";
    private static final String LINEA_COLUMNAS = "l.id_linea, l.id_pedido, l.referencia_articulo, l.descripcion, l.cantidad, l.precio_coste, l.descuento, l.iva, l.importe";

    Pagina<Proveedor> proveedores(int empresa, ConsultaParametros p) {
        String from = PROVEEDORES;
        var args = new ArrayList<Object>(List.of(empresa));
        if (!p.q().isEmpty()) {
            from += " AND (pr.codigo_proveedor LIKE ? ESCAPE '!' OR " + NOMBRE + " LIKE ? ESCAPE '!')";
            args.add(p.patron()); args.add(p.patron());
        }
        return pagina(PROVEEDOR_COLUMNAS, from, args, (rs, row) -> proveedor(rs), p);
    }
    Optional<ProveedorDetalle> proveedor(int empresa, int id, boolean financiero) {
        String columnas="pr.id_proveedor,pr.codigo_proveedor,"+NOMBRE+",pr.tipo_proveedor,e.nif_cif,e.telefono_fijo,e.movil,e.email,e.observaciones,pr.iban,e.activo";
        return jdbc.query("SELECT " + columnas + PROVEEDORES + " AND pr.id_proveedor = ?",
                (rs,row)->new ProveedorDetalle(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),
                    rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),
                    financiero?ConsultaRepository.enmascararIban(rs.getString(10)):null,rs.getBoolean(11)), empresa,id).stream().findFirst();
    }
    Pagina<Pedido> pedidos(int empresa, Integer proveedor, CompraParametros p) {
        String from = PEDIDOS + AMBITO;
        var args = new ArrayList<Object>(List.of(empresa));
        Integer filtro = proveedor != null ? proveedor : p.proveedor();
        if (filtro != null) { from += " AND p.id_proveedor = ?"; args.add(filtro); }
        var pagina = p.pagina();
        if (!pagina.q().isEmpty()) { from += " AND p.numero LIKE ? ESCAPE '!'"; args.add(pagina.patron()); }
        if (!pagina.estado().isEmpty()) { from += " AND s.descripcion = ?"; args.add(pagina.estado()); }
        if (pagina.desde() != null) { from += " AND p.fecha >= ?"; args.add(pagina.desde()); }
        if (pagina.hasta() != null) { from += " AND p.fecha <= ?"; args.add(pagina.hasta()); }
        return pagina(PEDIDO_COLUMNAS, from, args, (rs, row) -> pedido(rs), pagina);
    }
    Optional<Pedido> pedido(int empresa, int id) {
        return jdbc.query("SELECT " + PEDIDO_COLUMNAS + PEDIDOS + AMBITO + " AND p.id_pedido = ?",
                (rs, row) -> pedido(rs), empresa, id).stream().findFirst();
    }
    private static String lineasFrom() {
        return PEDIDOS + " JOIN Lineas_Pedido l ON l.id_pedido = p.id_pedido" + AMBITO + " AND p.id_pedido = ?";
    }
    Pagina<Linea> lineas(int empresa, int pedido, ConsultaParametros p) {
        return pagina(LINEA_COLUMNAS, lineasFrom(), List.of(empresa, pedido), (rs, row) -> linea(rs), p);
    }
    Optional<Linea> linea(int empresa, int pedido, int id) {
        return jdbc.query("SELECT " + LINEA_COLUMNAS + lineasFrom() + " AND l.id_linea = ?",
                (rs, row) -> linea(rs), empresa, pedido, id).stream().findFirst();
    }
    private static Proveedor proveedor(ResultSet rs) throws SQLException {
        return new Proveedor(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5));
    }
    private static Pedido pedido(ResultSet rs) throws SQLException {
        return new Pedido(rs.getInt(1), rs.getString(2), rs.getObject(3, LocalDate.class), rs.getString(4),
                new ReferenciaProveedor(rs.getInt(5), rs.getString(6), rs.getString(7)),
                decimal(rs, 8), decimal(rs, 9), decimal(rs, 10), decimal(rs, 11));
    }
    private static Linea linea(ResultSet rs) throws SQLException {
        return new Linea(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), decimal(rs, 5),
                decimal(rs, 6), decimal(rs, 7), decimal(rs, 8), decimal(rs, 9));
    }
    private static String decimal(ResultSet rs, int index) throws SQLException { return rs.getBigDecimal(index).toPlainString(); }
    private <T> Pagina<T> pagina(String select, String from, List<Object> args, RowMapper<T> mapper, ConsultaParametros p) {
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
        var pageArgs = new ArrayList<>(args);
        pageArgs.add(p.size()); pageArgs.add(p.offset());
        var items = jdbc.query("SELECT " + select + from + " ORDER BY " + p.orderBy() + " LIMIT ? OFFSET ?", mapper, pageArgs.toArray());
        return new Pagina<>(items, p.page(), p.size(), total, (total + p.size() - 1) / p.size(), p.sort());
    }
}
