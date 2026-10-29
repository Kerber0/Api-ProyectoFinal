package com.empresa.api.consulta;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import static com.empresa.api.consulta.InformeDtos.Estado;

@Repository
public class InformeRepository {
    private final JdbcTemplate jdbc;
    public InformeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    record Cifras(long cantidad, BigDecimal subtotal, BigDecimal descuento, BigDecimal impuestos,
                  BigDecimal total, BigDecimal movimientos) {
        static Cifras cero() {
            BigDecimal cero = new BigDecimal("0.00");
            return new Cifras(0, cero, cero, cero, cero, cero);
        }
        Cifras sumar(Cifras otra) {
            return new Cifras(cantidad + otra.cantidad, subtotal.add(otra.subtotal), descuento.add(otra.descuento),
                    impuestos.add(otra.impuestos), total.add(otra.total), movimientos.add(otra.movimientos));
        }
    }

    record EmpresaGrupo(int id, String nombre) {}
    List<EmpresaGrupo> empresasGrupo() {
        return jdbc.query("SELECT id_empresa,nombre FROM API_Empresas WHERE id_empresa IN (1,2,3,4) AND activo ORDER BY id_empresa",
                (rs,row)->new EmpresaGrupo(rs.getInt(1),rs.getString(2)));
    }

    Map<YearMonth, Cifras> facturacion(int empresa, EconomiaTipo tipo, InformeParametros p) {
        return agrupar(empresa, p, "f.fecha", "SUM(f.subtotal), SUM(f.descuento), SUM(f.impuestos), SUM(f.total), 0",
                EconomiaRepository.joins(tipo) + EconomiaRepository.ambito(tipo));
    }
    Map<YearMonth, Cifras> movimientos(int empresa, EconomiaTipo tipo, InformeParametros p) {
        return agrupar(empresa, p, "m.fecha", "0, 0, 0, 0, SUM(m.importe)", EconomiaRepository.movimientosFrom(tipo));
    }
    Map<YearMonth, Cifras> saldos(int empresa, EconomiaTipo tipo, InformeParametros p) {
        // Una fila por factura antes de agrupar: el acumulado no se multiplica por lineas.
        // El intervalo selecciona facturas emitidas; sus movimientos se toman completos.
        return agrupar(empresa, p, "f.fecha", "0, 0, 0, SUM(f.total), SUM(COALESCE(a.registrado, 0))",
                EconomiaRepository.joins(tipo) + EconomiaRepository.acumulado(tipo) + EconomiaRepository.ambito(tipo));
    }
    List<Estado> estadosObras(int empresa, InformeParametros p) {
        return jdbc.query("""
                SELECT s.id_estado,s.descripcion,s.color,s.orden_visualizacion,COUNT(o.id_obra)
                FROM Estados_Obra s
                LEFT JOIN (Obras o
                    JOIN API_Obra_Empresa oe ON oe.id_obra=o.id_obra AND oe.id_empresa=?
                    JOIN API_Cliente_Empresa ce ON ce.id_cliente=o.id_cliente AND ce.id_empresa=oe.id_empresa)
                  ON o.id_estado=s.id_estado AND o.fecha_inicio>=? AND o.fecha_inicio<=?
                GROUP BY s.id_estado,s.descripcion,s.color,s.orden_visualizacion
                ORDER BY s.orden_visualizacion,s.id_estado
                """,(rs,row)->new Estado(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getLong(5)),
                empresa,p.desde(),p.hasta());
    }
    List<Estado> estadosPresupuestos(int empresa, InformeParametros p) {
        return jdbc.query("""
                SELECT s.id_estado,s.descripcion,s.color,s.orden_visualizacion,COUNT(p.id_presupuesto)
                FROM Estados_Factura s
                LEFT JOIN (Presupuestos p
                    JOIN API_Presupuesto_Empresa pe ON pe.id_presupuesto=p.id_presupuesto AND pe.id_empresa=?
                    JOIN API_Cliente_Empresa ce ON ce.id_cliente=p.id_cliente AND ce.id_empresa=pe.id_empresa
                    LEFT JOIN Obras o ON o.id_obra=p.id_obra
                    LEFT JOIN API_Obra_Empresa oe ON oe.id_obra=o.id_obra)
                  ON p.id_estado=s.id_estado AND p.fecha>=? AND p.fecha<=?
                  AND (p.id_obra IS NULL OR (oe.id_empresa=pe.id_empresa AND o.id_cliente=p.id_cliente))
                GROUP BY s.id_estado,s.descripcion,s.color,s.orden_visualizacion
                ORDER BY s.orden_visualizacion,s.id_estado
                """,(rs,row)->new Estado(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getInt(4),rs.getLong(5)),
                empresa,p.desde(),p.hasta());
    }
    private Map<YearMonth, Cifras> agrupar(int empresa, InformeParametros p, String fecha, String importes, String from) {
        // Fechas e importes solo proceden de las tres consultas anteriores, nunca de HTTP.
        String sql = "SELECT EXTRACT(YEAR FROM " + fecha + "), EXTRACT(MONTH FROM " + fecha + "), COUNT(*), " + importes + from
                + " AND " + fecha + " >= ? AND " + fecha + " <= ? GROUP BY EXTRACT(YEAR FROM " + fecha + "), EXTRACT(MONTH FROM " + fecha + ")"
                + " ORDER BY EXTRACT(YEAR FROM " + fecha + "), EXTRACT(MONTH FROM " + fecha + ")";
        var result = new LinkedHashMap<YearMonth, Cifras>();
        jdbc.query(sql, (org.springframework.jdbc.core.RowCallbackHandler) rs -> result.put(
                YearMonth.of(rs.getInt(1), rs.getInt(2)), new Cifras(rs.getLong(3), rs.getBigDecimal(4),
                        rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8))),
                empresa, p.desde(), p.hasta());
        return result;
    }
}
