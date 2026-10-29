package com.empresa.api.consulta;

import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import static com.empresa.api.consulta.InformeDtos.*;
import com.empresa.api.consulta.InformeRepository.Cifras;

@Service
@Transactional(readOnly = true)
public class InformeService {
    private final AutorizacionService acceso;
    private final InformeRepository repository;
    public InformeService(AutorizacionService acceso, InformeRepository repository) {
        this.acceso = acceso; this.repository = repository;
    }
    private void autorizar(int usuario, int empresa, EconomiaTipo tipo, boolean movimientos) {
        acceso.exigirLectura(usuario, empresa, "INFORMES");
        acceso.exigirLectura(usuario, empresa, tipo.permisoFactura);
        if (movimientos) { acceso.exigirLectura(usuario, empresa, tipo.permisoMovimiento); }
    }
    public Informe<Facturacion> facturacion(int usuario, int empresa, EconomiaTipo tipo, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, tipo, false);
        var p = InformeParametros.parse(query);
        return informe(empresa, tipo, p, "FACTURAS_POR_FECHA_EMISION", repository.facturacion(empresa, tipo, p),
                c -> new Facturacion(c.cantidad(), decimal(c.subtotal()), decimal(c.descuento()), decimal(c.impuestos()), decimal(c.total())));
    }
    public Informe<Movimientos> movimientos(int usuario, int empresa, EconomiaTipo tipo, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, tipo, true);
        var p = InformeParametros.parse(query);
        return informe(empresa, tipo, p, "MOVIMIENTOS_POR_FECHA_EFECTIVA", repository.movimientos(empresa, tipo, p),
                c -> new Movimientos(c.cantidad(), decimal(c.movimientos())));
    }
    public Informe<Saldos> saldos(int usuario, int empresa, EconomiaTipo tipo, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, tipo, true);
        var p = InformeParametros.parse(query);
        return informe(empresa, tipo, p, "DEMO_FACTURAS_EMITIDAS_MENOS_MOVIMIENTOS_COMPLETOS", repository.saldos(empresa, tipo, p),
                c -> new Saldos(c.cantidad(), decimal(c.total()), decimal(c.movimientos()), decimal(c.total().subtract(c.movimientos()))));
    }
    public InformeEstados estadosObras(int usuario,int empresa,MultiValueMap<String,String> query) {
        acceso.exigirLectura(usuario,empresa,"INFORMES");
        acceso.exigirLectura(usuario,empresa,"OBRAS");
        var p=InformeParametros.parse(query);
        return estados(empresa,"OBRAS",p,"ESTADO_ACTUAL_POR_FECHA_INICIO",repository.estadosObras(empresa,p));
    }
    public InformeEstados estadosPresupuestos(int usuario,int empresa,MultiValueMap<String,String> query) {
        acceso.exigirLectura(usuario,empresa,"INFORMES");
        acceso.exigirLectura(usuario,empresa,"PRESUPUESTOS");
        var p=InformeParametros.parse(query);
        return estados(empresa,"PRESUPUESTOS",p,"ESTADO_ACTUAL_POR_FECHA_EMISION",repository.estadosPresupuestos(empresa,p));
    }
    private static InformeEstados estados(int empresa,String tipo,InformeParametros p,String criterio,List<Estado> estados) {
        return new InformeEstados(empresa,tipo,p.desde(),p.hasta(),criterio,
                estados.stream().mapToLong(Estado::cantidad).sum(),List.copyOf(estados));
    }
    private static String decimal(BigDecimal value) { return value.setScale(2).toPlainString(); }

    private static <T> Informe<T> informe(int empresa, EconomiaTipo tipo, InformeParametros p, String criterio,
                                          Map<YearMonth, Cifras> valores, Function<Cifras, T> convertir) {
        var meses = new ArrayList<Mes<T>>();
        Cifras total = Cifras.cero();
        YearMonth fin = YearMonth.from(p.hasta());
        for (YearMonth mes = YearMonth.from(p.desde()); !mes.isAfter(fin); mes = mes.plusMonths(1)) {
            Cifras cifras = valores.getOrDefault(mes, Cifras.cero());
            meses.add(new Mes<>(mes.toString(), convertir.apply(cifras)));
            total = total.sumar(cifras);
        }
        return new Informe<>(empresa, tipo.name(), p.desde(), p.hasta(), criterio, List.copyOf(meses), convertir.apply(total));
    }
}
