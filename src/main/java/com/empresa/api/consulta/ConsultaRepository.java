package com.empresa.api.consulta;

import com.empresa.api.common.Pagina;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.empresa.api.consulta.ConsultaDtos.*;

@Repository
public class ConsultaRepository {
    private final JdbcTemplate jdbc;
    public ConsultaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String NOMBRE = "COALESCE(NULLIF(e.razon_social, ''), NULLIF(TRIM(CONCAT_WS(' ', e.nombre, e.apellidos)), ''))";
    private static final String CLIENTES = """
            FROM Clientes c JOIN Entidades e ON e.id_entidad = c.id_entidad
            JOIN API_Cliente_Empresa ce ON ce.id_cliente = c.id_cliente
            WHERE ce.id_empresa = ?
            """;
    // Si el cliente de una obra pertenece a otra empresa, la obra queda oculta.
    private static final String OBRAS = """
            FROM Obras o JOIN API_Obra_Empresa oe ON oe.id_obra = o.id_obra
            JOIN Clientes c ON c.id_cliente = o.id_cliente
            JOIN API_Cliente_Empresa ce ON ce.id_cliente = c.id_cliente AND ce.id_empresa = oe.id_empresa
            JOIN Entidades e ON e.id_entidad = c.id_entidad
            JOIN Estados_Obra s ON s.id_estado = o.id_estado
            LEFT JOIN Direcciones d ON d.id_direccion = o.id_direccion AND d.id_entidad = c.id_entidad
            WHERE oe.id_empresa = ?
            """;

    Pagina<ClienteResumen> clientes(int empresa, ConsultaParametros p) {
        var args = new ArrayList<Object>(List.of(empresa));
        String from = CLIENTES;
        if (!p.q().isEmpty()) {
            from += " AND (c.codigo_cliente LIKE ? ESCAPE '!' OR " + NOMBRE + " LIKE ? ESCAPE '!')";
            args.add(p.patron()); args.add(p.patron());
        }
        return pagina("c.id_cliente, c.codigo_cliente, " + NOMBRE + " AS nombre_cliente, e.activo", from, args,
                (rs, row) -> new ClienteResumen(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)), p);
    }

    Optional<ClienteDetalle> cliente(int empresa, int id, boolean financiero) {
        return jdbc.query("SELECT c.id_cliente, c.codigo_cliente, " + NOMBRE + " AS nombre_cliente, " +
                        "e.tipo_entidad, e.nif_cif, e.telefono_fijo, e.movil, e.email, e.observaciones, c.iban, e.activo " + CLIENTES + " AND c.id_cliente = ?",
                (rs, row) -> new ClienteDetalle(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                        financiero ? enmascararIban(rs.getString(10)) : null, rs.getBoolean(11)), empresa, id)
                .stream().findFirst();
    }

    Pagina<ObraResumen> obras(int empresa, ConsultaParametros p) {
        var args = new ArrayList<Object>(List.of(empresa));
        String from = OBRAS;
        if (!p.q().isEmpty()) {
            from += " AND (o.codigo_obra LIKE ? ESCAPE '!' OR o.titulo LIKE ? ESCAPE '!')";
            args.add(p.patron()); args.add(p.patron());
        }
        if (!p.estado().isEmpty()) { from += " AND s.descripcion = ?"; args.add(p.estado()); }
        if (p.cliente() != null) { from += " AND o.id_cliente = ?"; args.add(p.cliente()); }
        return pagina("o.id_obra, o.codigo_obra, o.titulo, s.descripcion, o.id_cliente", from, args,
                (rs, row) -> new ObraResumen(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)), p);
    }

    Optional<ObraDetalle> obra(int empresa, int id) {
        String select = """
                SELECT o.id_obra, o.codigo_obra, o.titulo, o.descripcion, o.presupuesto,
                o.fecha_inicio, o.fecha_fin_prevista, o.fecha_fin_real, s.descripcion AS estado,
                c.id_cliente, c.codigo_cliente,
                """ + NOMBRE + " AS nombre_cliente, " + DIRECCION_COLUMNS + " ";
        return jdbc.query(select + OBRAS + " AND o.id_obra = ?", (rs, row) -> new ObraDetalle(
                rs.getInt("id_obra"), rs.getString("codigo_obra"), rs.getString("titulo"), rs.getString("descripcion"),
                rs.getBigDecimal("presupuesto").toPlainString(), rs.getObject("fecha_inicio", LocalDate.class),
                rs.getObject("fecha_fin_prevista", LocalDate.class), rs.getObject("fecha_fin_real", LocalDate.class),
                rs.getString("estado"), new ReferenciaCliente(rs.getInt("id_cliente"), rs.getString("codigo_cliente"),
                rs.getString("nombre_cliente")), rs.getObject("id_direccion") == null ? null : direccion(rs)), empresa, id)
                .stream().findFirst();
    }

    static String enmascararIban(String iban) { return iban == null ? null : "****" + iban.substring(Math.max(0, iban.length()-4)); }
    private static final String CONTACTO_COLUMNS = "k.id_contacto, k.nombre, k.apellidos, k.cargo, k.telefono, k.movil, k.email, k.departamento, k.observaciones, k.principal";
    private static final String DIRECCION_COLUMNS = "d.id_direccion, d.tipo_direccion, d.direccion, d.codigo_postal, d.poblacion, d.provincia, d.pais, d.telefono, d.observaciones, d.principal";

    Pagina<Contacto> contactos(int empresa, int cliente, ConsultaParametros p) {
        String from = """
                FROM Contactos k JOIN Clientes c ON c.id_entidad = k.id_entidad
                JOIN API_Cliente_Empresa ce ON ce.id_cliente = c.id_cliente
                WHERE ce.id_empresa = ? AND c.id_cliente = ? AND k.activo
                """;
        return pagina(CONTACTO_COLUMNS, from, List.of(empresa, cliente), (rs, row) -> contacto(rs), p);
    }

    Pagina<Direccion> direcciones(int empresa, int cliente, ConsultaParametros p) {
        String from = """
                FROM Direcciones d JOIN Clientes c ON c.id_entidad = d.id_entidad
                JOIN API_Cliente_Empresa ce ON ce.id_cliente = c.id_cliente
                WHERE ce.id_empresa = ? AND c.id_cliente = ? AND d.activo
                """;
        return pagina(DIRECCION_COLUMNS, from, List.of(empresa, cliente), (rs, row) -> direccion(rs), p);
    }

    Pagina<Contacto> contactosProveedor(int empresa, int proveedor, ConsultaParametros p) {
        String from = " FROM Contactos k JOIN Proveedores pr ON pr.id_entidad=k.id_entidad JOIN API_Proveedor_Empresa pe ON pe.id_proveedor=pr.id_proveedor WHERE pe.id_empresa=? AND pr.id_proveedor=? AND k.activo";
        return pagina(CONTACTO_COLUMNS, from, List.of(empresa, proveedor), (rs,row)->contacto(rs), p);
    }
    Pagina<Direccion> direccionesProveedor(int empresa, int proveedor, ConsultaParametros p) {
        String from = " FROM Direcciones d JOIN Proveedores pr ON pr.id_entidad=d.id_entidad JOIN API_Proveedor_Empresa pe ON pe.id_proveedor=pr.id_proveedor WHERE pe.id_empresa=? AND pr.id_proveedor=? AND d.activo";
        return pagina(DIRECCION_COLUMNS, from, List.of(empresa, proveedor), (rs,row)->direccion(rs), p);
    }

    Pagina<ContactoObra> contactosObra(int empresa, int obra, ConsultaParametros p) {
        String from = """
                FROM Obra_Contacto oc JOIN Obras o ON o.id_obra = oc.id_obra
                JOIN API_Obra_Empresa oe ON oe.id_obra = o.id_obra
                JOIN Clientes c ON c.id_cliente = o.id_cliente
                JOIN API_Cliente_Empresa ce ON ce.id_cliente = c.id_cliente AND ce.id_empresa = oe.id_empresa
                JOIN Contactos k ON k.id_contacto = oc.id_contacto AND k.id_entidad = c.id_entidad
                WHERE oe.id_empresa = ? AND o.id_obra = ? AND k.activo
                """;
        return pagina("oc.id_obra_contacto, oc.rol_en_obra, " + CONTACTO_COLUMNS, from, List.of(empresa, obra),
                (rs, row) -> new ContactoObra(rs.getInt("id_obra_contacto"), contacto(rs), rs.getString("rol_en_obra")), p);
    }

    private static Contacto contacto(ResultSet rs) throws SQLException {
        return new Contacto(rs.getInt("id_contacto"), rs.getString("nombre"), rs.getString("apellidos"),
                rs.getString("cargo"), rs.getString("telefono"), rs.getString("movil"), rs.getString("email"),
                rs.getString("departamento"), rs.getString("observaciones"), rs.getBoolean("principal"));
    }
    private static Direccion direccion(ResultSet rs) throws SQLException {
        return new Direccion(rs.getInt("id_direccion"), rs.getString("tipo_direccion"), rs.getString("direccion"), rs.getString("codigo_postal"),
                rs.getString("poblacion"), rs.getString("provincia"), rs.getString("pais"), rs.getString("telefono"),
                rs.getString("observaciones"), rs.getBoolean("principal"));
    }
    private <T> Pagina<T> pagina(String select, String from, List<Object> args, RowMapper<T> mapper, ConsultaParametros p) {
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
        var pageArgs = new ArrayList<>(args);
        pageArgs.add(p.size()); pageArgs.add(p.offset());
        // orderBy solo procede de la lista cerrada en ConsultaParametros.
        var items = jdbc.query("SELECT " + select + " " + from + " ORDER BY " + p.orderBy() + " LIMIT ? OFFSET ?", mapper, pageArgs.toArray());
        return new Pagina<>(items, p.page(), p.size(), total, (total + p.size() - 1) / p.size(), p.sort());
    }
}
