package com.empresa.api.tesoreria;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static com.empresa.api.tesoreria.PrevisionDtos.*;

@Repository
public class PrevisionRepository {
    private final JdbcTemplate jdbc;
    public PrevisionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Importes firmados: las rectificativas pueden compensar vencimientos positivos.
    private static final String PENDIENTES = """
        WITH aplicaciones AS (
            SELECT a.vencimiento, SUM(a.importe) AS aplicado
            FROM API_Tesoreria_Aplicaciones a
            JOIN API_Tesoreria m ON m.id=a.movimiento
            WHERE m.estado <> 'REVERTIDO'
            GROUP BY a.vencimiento
        ), pendientes AS (
            SELECT v.id, d.id AS documento, COALESCE(d.factura_cliente,d.factura_proveedor) AS factura,
                   COALESCE(d.cliente,d.proveedor) AS tercero, d.tipo,
                   v.posicion, v.fecha, v.importe, COALESCE(a.aplicado,0.00) AS aplicado,
                   v.importe - COALESCE(a.aplicado,0.00) AS saldo
            FROM API_Vencimientos v JOIN API_Facturacion d ON d.id=v.documento
            LEFT JOIN aplicaciones a ON a.vencimiento=v.id
            WHERE d.id_empresa=? AND d.tipo IN (?,?) AND d.estado IN ('EMITIDA','RECTIFICADA')
                  AND v.fecha>=? AND v.fecha<=?
        )
        """;
    private static final String FILTRO = " FROM pendientes WHERE saldo<>0";

    public Prevision consultar(int empresa,LocalDate desde,LocalDate hasta,String tipo,int page,int size,LocalDate hoy) {
        String cliente=tipo.equals("PROVEEDOR")?"PROVEEDOR":"CLIENTE";
        String proveedor=tipo.equals("CLIENTE")?"CLIENTE":"PROVEEDOR";
        Object[] parametros={empresa,cliente,proveedor,desde,hasta};
        var resumen=jdbc.queryForObject(PENDIENTES+" SELECT COUNT(*) AS cuenta,"+
                " COALESCE(SUM(saldo),0.00) AS total,"+
                " COALESCE(SUM(CASE WHEN tipo='CLIENTE' THEN saldo ELSE 0 END),0.00) AS cobrar,"+
                " COALESCE(SUM(CASE WHEN tipo='PROVEEDOR' THEN saldo ELSE 0 END),0.00) AS pagar"+FILTRO,
                (rs,n)->new Resumen(rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3),rs.getBigDecimal(4)),parametros);
        var items=jdbc.query(PENDIENTES+" SELECT id,documento,factura,tercero,tipo,posicion,fecha,importe,aplicado,saldo"+FILTRO+
                " ORDER BY fecha ASC,id ASC LIMIT ? OFFSET ?",(rs,n)->{
                    BigDecimal saldo=rs.getBigDecimal(10);
                    LocalDate fecha=rs.getObject(7,LocalDate.class);
                    String estado=saldo.signum()<0?"AJUSTE":fecha.isBefore(hoy)?"VENCIDO":"PENDIENTE";
                    return new VencimientoPendiente(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4),
                            rs.getString(5),rs.getInt(6),fecha,rs.getBigDecimal(8).toPlainString(),
                            rs.getBigDecimal(9).toPlainString(),saldo.toPlainString(),estado);
                },empresa,cliente,proveedor,desde,hasta,size,(long)page*size);
        return new Prevision(empresa,desde,hasta,tipo,resumen.cuenta(),dinero(resumen.total()),
                dinero(resumen.cobrar()),dinero(resumen.pagar()),page,size,List.copyOf(items));
    }
    private static String dinero(BigDecimal importe) { return importe.setScale(2).toPlainString(); }
    private record Resumen(long cuenta,BigDecimal total,BigDecimal cobrar,BigDecimal pagar) {}
}
