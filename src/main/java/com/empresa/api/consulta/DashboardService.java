package com.empresa.api.consulta;

import com.empresa.api.consulta.InformeService;
import com.empresa.api.consulta.EconomiaTipo;
import com.empresa.api.security.AutorizacionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import static com.empresa.api.consulta.DashboardDtos.*;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final InformeService informes;
    private final AutorizacionService autorizacion;
    private final JdbcTemplate jdbc;

    public DashboardService(InformeService informes, AutorizacionService autorizacion, JdbcTemplate jdbc) {
        this.informes = informes;
        this.autorizacion = autorizacion;
        this.jdbc = jdbc;
    }

    public Resumen consultar(int usuario, int empresa, MultiValueMap<String, String> filtros) {
        // Las consultas de informes vuelven a comprobar INFORMES y permisos de facturas y movimientos.
        autorizacion.exigirLectura(usuario, empresa, "OBRAS");
        autorizacion.exigirLectura(usuario, empresa, "PRESUPUESTOS");
        var facturasCliente = informes.facturacion(usuario, empresa, EconomiaTipo.CLIENTE, filtros);
        var facturasProveedor = informes.facturacion(usuario, empresa, EconomiaTipo.PROVEEDOR, filtros);
        var cobros = informes.movimientos(usuario, empresa, EconomiaTipo.CLIENTE, filtros);
        var pagos = informes.movimientos(usuario, empresa, EconomiaTipo.PROVEEDOR, filtros);
        long obras = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Obras o
                JOIN API_Obra_Empresa oe ON oe.id_obra = o.id_obra
                JOIN API_Cliente_Empresa ce ON ce.id_cliente = o.id_cliente AND ce.id_empresa = oe.id_empresa
                WHERE oe.id_empresa = ?
                """, Long.class, empresa);
        long presupuestos = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Presupuestos p
                JOIN API_Presupuesto_Empresa pe ON pe.id_presupuesto = p.id_presupuesto
                JOIN API_Cliente_Empresa ce ON ce.id_cliente = p.id_cliente AND ce.id_empresa = pe.id_empresa
                LEFT JOIN Obras o ON o.id_obra = p.id_obra
                LEFT JOIN API_Obra_Empresa oe ON oe.id_obra = o.id_obra
                WHERE pe.id_empresa = ? AND (p.id_obra IS NULL OR
                      (oe.id_empresa = pe.id_empresa AND o.id_cliente = p.id_cliente))
                """, Long.class, empresa);
        return new Resumen(empresa, facturasCliente.desde(), facturasCliente.hasta(),
                facturasCliente.totales(), facturasProveedor.totales(),
                cobros.totales(), pagos.totales(), obras, presupuestos);
    }
}
