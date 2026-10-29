package com.empresa.api.consulta;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.empresa.api.consulta.LaboralDtos.*;
import com.empresa.api.common.Pagina;

@Repository
public class LaboralRepository {
    private final JdbcTemplate jdbc;
    public LaboralRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String NOMBRE = "NULLIF(TRIM(CONCAT_WS(' ', e.nombre, e.apellidos)), '')";
    private static final String OPERARIOS = """
            FROM Operarios op JOIN API_Operario_Empresa ope ON ope.id_operario = op.id_operario
            JOIN Entidades e ON e.id_entidad = op.id_entidad AND e.tipo_entidad = 'PERSONA'
            LEFT JOIN Categorias_Operarios cat ON cat.id_categoria = op.id_categoria
            WHERE ope.id_empresa = ?
            """;
    private static final String OPERARIO_COLUMNAS = "op.id_operario, op.codigo_operario, " + NOMBRE
            + " AS nombre_operario, cat.descripcion, op.fecha_alta, op.fecha_baja";
    // La pertenencia del parte se deriva de obra, cliente y operario coherentes.
    // Las bajas o categorias inactivas no eliminan registros historicos.
    private static final String PARTES = """
            FROM Partes_Mano_Obra pm JOIN Obras o ON o.id_obra = pm.id_obra
            JOIN API_Obra_Empresa oe ON oe.id_obra = o.id_obra
            JOIN API_Cliente_Empresa ce ON ce.id_cliente = o.id_cliente AND ce.id_empresa = oe.id_empresa
            JOIN Operarios op ON op.id_operario = pm.id_operario
            JOIN API_Operario_Empresa ope ON ope.id_operario = op.id_operario AND ope.id_empresa = oe.id_empresa
            JOIN Entidades e ON e.id_entidad = op.id_entidad AND e.tipo_entidad = 'PERSONA'
            WHERE oe.id_empresa = ?
            """;
    private static final String PARTE_COLUMNAS = "pm.id_parte, pm.id_obra, pm.id_operario, pm.fecha, pm.hora_inicio, pm.hora_fin, pm.horas_trabajadas";

    Pagina<Operario> operarios(int empresa, ConsultaParametros p) {
        String from = OPERARIOS;
        var args = new ArrayList<Object>(List.of(empresa));
        if (!p.q().isEmpty()) {
            from += " AND (op.codigo_operario LIKE ? ESCAPE '!' OR " + NOMBRE + " LIKE ? ESCAPE '!')";
            args.add(p.patron()); args.add(p.patron());
        }
        return pagina(OPERARIO_COLUMNAS, from, args, (rs, row) -> operario(rs), p);
    }

    Optional<Operario> operario(int empresa, int id) {
        return jdbc.query("SELECT " + OPERARIO_COLUMNAS + " " + OPERARIOS + " AND op.id_operario = ?",
                (rs, row) -> operario(rs), empresa, id).stream().findFirst();
    }

    Pagina<Parte> partes(int empresa, Integer obra, Integer operario, ConsultaParametros p) {
        String from = PARTES;
        var args = new ArrayList<Object>(List.of(empresa));
        if (obra != null) { from += " AND pm.id_obra = ?"; args.add(obra); }
        if (operario != null) { from += " AND pm.id_operario = ?"; args.add(operario); }
        if (p.desde() != null) { from += " AND pm.fecha >= ?"; args.add(p.desde()); }
        if (p.hasta() != null) { from += " AND pm.fecha <= ?"; args.add(p.hasta()); }
        return pagina(PARTE_COLUMNAS, from, args, (rs, row) -> parte(rs), p);
    }

    Optional<Parte> parte(int empresa, int id) {
        return jdbc.query("SELECT " + PARTE_COLUMNAS + " " + PARTES + " AND pm.id_parte = ?",
                (rs, row) -> parte(rs), empresa, id).stream().findFirst();
    }

    private static Operario operario(ResultSet rs) throws SQLException {
        return new Operario(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class));
    }
    private static Parte parte(ResultSet rs) throws SQLException {
        return new Parte(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getObject(4, LocalDate.class),
                rs.getObject(5, LocalTime.class), rs.getObject(6, LocalTime.class), rs.getBigDecimal(7).toPlainString());
    }
    private <T> Pagina<T> pagina(String select, String from, List<Object> args, RowMapper<T> mapper, ConsultaParametros p) {
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
        var pageArgs = new ArrayList<>(args);
        pageArgs.add(p.size()); pageArgs.add(p.offset());
        var items = jdbc.query("SELECT " + select + " " + from + " ORDER BY " + p.orderBy() + " LIMIT ? OFFSET ?", mapper, pageArgs.toArray());
        return new Pagina<>(items, p.page(), p.size(), total, (total + p.size() - 1) / p.size(), p.sort());
    }
}
