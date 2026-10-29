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
import static com.empresa.api.consulta.CalendarioDtos.*;
import com.empresa.api.common.Pagina;

@Repository
public class CalendarioRepository {
    private final JdbcTemplate jdbc;
    public CalendarioRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String OBRA_COHERENTE = """
             AND (x.id_obra IS NULL OR EXISTS (
                 SELECT 1 FROM Obras o JOIN API_Obra_Empresa oe ON oe.id_obra = o.id_obra
                 JOIN API_Cliente_Empresa ce ON ce.id_cliente = o.id_cliente AND ce.id_empresa = oe.id_empresa
                 WHERE o.id_obra = x.id_obra AND oe.id_empresa = a.id_empresa
             ))
            """;
    // Solo calendario operativo; PERSONAL y VACACIONES no se publican, ni por ID.
    private static final String EVENTOS = """
            FROM Eventos x JOIN API_Evento_Empresa a ON a.id_evento = x.id_evento
            WHERE a.id_empresa = ? AND x.tipo_evento IN ('OBRA', 'REUNION', 'AVISO')
                AND (x.fecha_fin IS NULL OR x.fecha_fin >= x.fecha_inicio)
            """ + OBRA_COHERENTE;
    private static final String TAREAS = """
            FROM Tareas x JOIN API_Tarea_Empresa a ON a.id_tarea = x.id_tarea
            JOIN Estados_Tarea s ON s.id_estado = x.id_estado JOIN Prioridades p ON p.id_prioridad = x.id_prioridad
            WHERE a.id_empresa = ? AND (x.id_operario IS NULL OR EXISTS (
                SELECT 1 FROM Operarios op JOIN API_Operario_Empresa ope ON ope.id_operario = op.id_operario
                JOIN Entidades e ON e.id_entidad = op.id_entidad AND e.tipo_entidad = 'PERSONA'
                WHERE op.id_operario = x.id_operario AND ope.id_empresa = a.id_empresa
            ))
            """ + OBRA_COHERENTE;
    private static final String CAMPOS_EVENTO = "x.id_evento, x.titulo, x.fecha_inicio, x.fecha_fin, x.tipo_evento, x.id_obra";
    private static final String CAMPOS_TAREA = "x.id_tarea, x.titulo, x.fecha_limite, x.fecha_finalizacion, s.descripcion, p.descripcion, x.id_obra, x.id_operario";

    Pagina<Evento> eventos(int empresa, Integer obra, CalendarioParametros parametros) {
        String from = EVENTOS;
        var args = new ArrayList<Object>(List.of(empresa));
        var p = parametros.pagina();
        if (obra != null) { from += " AND x.id_obra = ?"; args.add(obra); }
        if (!p.q().isEmpty()) { from += " AND x.titulo LIKE ? ESCAPE '!'"; args.add(p.patron()); }
        if (!parametros.filtro().isEmpty()) { from += " AND x.tipo_evento = ?"; args.add(parametros.filtro()); }
        // Interseccion inclusiva con dias locales. Sin fin, solo se conoce el instante de inicio.
        if (p.desde() != null) { from += " AND COALESCE(x.fecha_fin, x.fecha_inicio) >= ?"; args.add(p.desde().atStartOfDay()); }
        if (p.hasta() != null) { from += " AND x.fecha_inicio < ?"; args.add(p.hasta().plusDays(1).atStartOfDay()); }
        return pagina(CAMPOS_EVENTO, from, args, (rs, row) -> evento(rs), p);
    }
    Optional<EventoDetalle> evento(int empresa, int id) {
        return jdbc.query("SELECT " + CAMPOS_EVENTO + ", x.descripcion " + EVENTOS + " AND x.id_evento = ?",
                (rs, row) -> new EventoDetalle(rs.getInt(1), rs.getString(2), rs.getObject(3, LocalDateTime.class),
                        rs.getObject(4, LocalDateTime.class), rs.getString(5), rs.getObject(6, Integer.class), rs.getString(7)),
                empresa, id).stream().findFirst();
    }
    Pagina<Tarea> tareas(int empresa, Integer obra, Integer operario, CalendarioParametros parametros) {
        String from = TAREAS;
        var args = new ArrayList<Object>(List.of(empresa));
        var p = parametros.pagina();
        if (obra != null) { from += " AND x.id_obra = ?"; args.add(obra); }
        if (operario != null) { from += " AND x.id_operario = ?"; args.add(operario); }
        if (!p.q().isEmpty()) { from += " AND x.titulo LIKE ? ESCAPE '!'"; args.add(p.patron()); }
        if (!p.estado().isEmpty()) { from += " AND s.descripcion = ?"; args.add(p.estado()); }
        if (!parametros.filtro().isEmpty()) { from += " AND p.descripcion = ?"; args.add(parametros.filtro()); }
        if (p.desde() != null) { from += " AND x.fecha_limite >= ?"; args.add(p.desde()); }
        if (p.hasta() != null) { from += " AND x.fecha_limite <= ?"; args.add(p.hasta()); }
        return pagina(CAMPOS_TAREA, from, args, (rs, row) -> tarea(rs), p);
    }
    Optional<TareaDetalle> tarea(int empresa, int id) {
        return jdbc.query("SELECT " + CAMPOS_TAREA + ", x.descripcion " + TAREAS + " AND x.id_tarea = ?",
                (rs, row) -> new TareaDetalle(rs.getInt(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                        rs.getObject(4, LocalDate.class), rs.getString(5), rs.getString(6), rs.getObject(7, Integer.class),
                        rs.getObject(8, Integer.class), rs.getString(9)), empresa, id).stream().findFirst();
    }
    private static Evento evento(ResultSet rs) throws SQLException {
        return new Evento(rs.getInt(1), rs.getString(2), rs.getObject(3, LocalDateTime.class),
                rs.getObject(4, LocalDateTime.class), rs.getString(5), rs.getObject(6, Integer.class));
    }
    private static Tarea tarea(ResultSet rs) throws SQLException {
        return new Tarea(rs.getInt(1), rs.getString(2), rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class),
                rs.getString(5), rs.getString(6), rs.getObject(7, Integer.class), rs.getObject(8, Integer.class));
    }
    private <T> Pagina<T> pagina(String columnas, String from, List<Object> args, RowMapper<T> mapper, ConsultaParametros p) {
        long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, args.toArray());
        var paginaArgs = new ArrayList<>(args);
        paginaArgs.add(p.size()); paginaArgs.add(p.offset());
        var items = jdbc.query("SELECT " + columnas + " " + from + " ORDER BY " + p.orderBy() + " LIMIT ? OFFSET ?", mapper, paginaArgs.toArray());
        return new Pagina<>(items, p.page(), p.size(), total, (total + p.size() - 1) / p.size(), p.sort());
    }
}
