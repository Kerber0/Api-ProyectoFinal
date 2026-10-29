package com.empresa.api.consulta;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static com.empresa.api.consulta.IncidenciaDtos.*;
import com.empresa.api.common.Pagina;

@Repository
public class IncidenciaRepository {
    private final JdbcTemplate jdbc;
    public IncidenciaRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Una asignacion incoherente excluye la incidencia completa, incluidos conteos.
    // Las bajas de catalogos y operarios conservan el historico.
    private static final String AMBITO = """
            FROM Incidencias i
            JOIN API_Incidencia_Empresa ie ON ie.id_incidencia = i.id_incidencia
            JOIN Obras o ON o.id_obra = i.id_obra
            JOIN API_Obra_Empresa oe ON oe.id_obra = o.id_obra AND oe.id_empresa = ie.id_empresa
            JOIN API_Cliente_Empresa ce ON ce.id_cliente = o.id_cliente AND ce.id_empresa = ie.id_empresa
            JOIN Estados_Incidencia s ON s.id_estado = i.id_estado
            JOIN Prioridades p ON p.id_prioridad = i.id_prioridad
            WHERE ie.id_empresa = ? AND (i.id_operario IS NULL OR EXISTS (
                SELECT 1 FROM Operarios op
                JOIN API_Operario_Empresa ope ON ope.id_operario = op.id_operario AND ope.id_empresa = ie.id_empresa
                JOIN Entidades e ON e.id_entidad = op.id_entidad AND e.tipo_entidad = 'PERSONA'
                WHERE op.id_operario = i.id_operario
            ))
            """;
    private static final String COLUMNAS = "i.id_incidencia, i.id_obra, i.id_operario, i.fecha, s.descripcion, p.descripcion";

    Pagina<Resumen> incidencias(int empresa, Integer obra, Integer operario, IncidenciaParametros parametros) {
        var pagina = parametros.pagina();
        String from = AMBITO;
        var args = new ArrayList<Object>(List.of(empresa));
        if (obra != null) { from += " AND i.id_obra = ?"; args.add(obra); }
        if (operario != null) { from += " AND i.id_operario = ?"; args.add(operario); }
        if (!pagina.estado().isEmpty()) { from += " AND s.descripcion = ?"; args.add(pagina.estado()); }
        if (!parametros.prioridad().isEmpty()) { from += " AND p.descripcion = ?"; args.add(parametros.prioridad()); }
        if (pagina.desde() != null) { from += " AND i.fecha >= ?"; args.add(pagina.desde()); }
        if (pagina.hasta() != null) { from += " AND i.fecha <= ?"; args.add(pagina.hasta()); }
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
        args.add(pagina.size()); args.add(pagina.offset());
        var items = jdbc.query("SELECT " + COLUMNAS + " " + from + " ORDER BY " + pagina.orderBy() + " LIMIT ? OFFSET ?",
                (rs, row) -> resumen(rs), args.toArray());
        return new Pagina<>(items, pagina.page(), pagina.size(), total, (total + pagina.size() - 1) / pagina.size(), pagina.sort());
    }

    Optional<Detalle> incidencia(int empresa, int id) {
        return jdbc.query("SELECT " + COLUMNAS + ", i.descripcion " + AMBITO + " AND i.id_incidencia = ?",
                (rs, row) -> new Detalle(rs.getInt(1), rs.getInt(2), rs.getObject(3, Integer.class),
                        rs.getObject(4, LocalDate.class), rs.getString(5), rs.getString(6), rs.getString(7)),
                empresa, id).stream().findFirst();
    }
    private static Resumen resumen(ResultSet rs) throws SQLException {
        return new Resumen(rs.getInt(1), rs.getInt(2), rs.getObject(3, Integer.class),
                rs.getObject(4, LocalDate.class), rs.getString(5), rs.getString(6));
    }
}
