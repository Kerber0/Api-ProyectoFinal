package com.empresa.api.consulta;

import com.empresa.api.security.AutorizacionService;
import com.empresa.api.common.ApiException;
import com.empresa.api.tesoreria.PrevisionService;
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
    private final PrevisionService previsiones;
    public InformeService(AutorizacionService acceso, InformeRepository repository, PrevisionService previsiones) {
        this.acceso = acceso; this.repository = repository; this.previsiones = previsiones;
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
    public InformeGrupo grupo(int usuario,int empresa,MultiValueMap<String,String> query) {
        var sociedades=repository.empresasGrupo();
        if(sociedades.size()!=4 || sociedades.stream().noneMatch(e->e.id()==empresa)) throw ApiException.noEncontrado();
        // Autorizar el grupo completo antes de validar filtros evita oraculos de parametros
        // para usuarios que solo pueden ver una parte de las sociedades.
        for(var sociedad:sociedades) {
            acceso.exigirLectura(usuario,sociedad.id(),"EMPRESA");
            acceso.exigirLectura(usuario,sociedad.id(),"INFORMES");
            acceso.exigirLectura(usuario,sociedad.id(),"FACTURAS_CLIENTE");
            acceso.exigirLectura(usuario,sociedad.id(),"COBROS");
            acceso.exigirLectura(usuario,sociedad.id(),"FACTURAS_PROVEEDOR");
            acceso.exigirLectura(usuario,sociedad.id(),"PAGOS");
        }
        var p=InformeParametros.parse(query);
        var resultados=new ArrayList<EmpresaEconomica>();
        TotalesEconomicos total=ceroEconomico();
        for(var sociedad:sociedades) {
            var valores=economico(usuario,sociedad.id(),query);
            resultados.add(new EmpresaEconomica(sociedad.id(),sociedad.nombre(),valores));
            total=sumar(total,valores);
        }
        return new InformeGrupo(p.desde(),p.hasta(),"AGREGADO_SIN_ELIMINACIONES_INTEREMPRESA",List.copyOf(resultados),total);
    }
    private TotalesEconomicos economico(int usuario,int empresa,MultiValueMap<String,String> query) {
        var fc=facturacion(usuario,empresa,EconomiaTipo.CLIENTE,query).totales();
        var fp=facturacion(usuario,empresa,EconomiaTipo.PROVEEDOR,query).totales();
        var cobros=movimientos(usuario,empresa,EconomiaTipo.CLIENTE,query).totales();
        var pagos=movimientos(usuario,empresa,EconomiaTipo.PROVEEDOR,query).totales();
        var pendienteCobro=saldos(usuario,empresa,EconomiaTipo.CLIENTE,query).totales();
        var pendientePago=saldos(usuario,empresa,EconomiaTipo.PROVEEDOR,query).totales();
        var prevision=previsiones.consultar(usuario,empresa,query);
        BigDecimal entradas=new BigDecimal(prevision.porCobrarFirmado());
        BigDecimal salidas=new BigDecimal(prevision.porPagarFirmado());
        return new TotalesEconomicos(fc,fp,cobros,pagos,pendienteCobro,pendientePago,decimal(entradas),decimal(salidas),
                decimal(new BigDecimal(cobros.importe()).subtract(new BigDecimal(pagos.importe()))),decimal(entradas.subtract(salidas)));
    }
    private static TotalesEconomicos ceroEconomico() {
        return new TotalesEconomicos(new Facturacion(0,"0.00","0.00","0.00","0.00"),new Facturacion(0,"0.00","0.00","0.00","0.00"),
                new Movimientos(0,"0.00"),new Movimientos(0,"0.00"),new Saldos(0,"0.00","0.00","0.00"),
                new Saldos(0,"0.00","0.00","0.00"),"0.00","0.00","0.00","0.00");
    }
    private static TotalesEconomicos sumar(TotalesEconomicos a,TotalesEconomicos b) {
        return new TotalesEconomicos(sumar(a.facturacionClientes(),b.facturacionClientes()),sumar(a.facturacionProveedores(),b.facturacionProveedores()),
                sumar(a.cobrosRegistrados(),b.cobrosRegistrados()),sumar(a.pagosRegistrados(),b.pagosRegistrados()),
                sumar(a.pendienteCobro(),b.pendienteCobro()),sumar(a.pendientePago(),b.pendientePago()),
                suma(a.previsionEntradas(),b.previsionEntradas()),suma(a.previsionSalidas(),b.previsionSalidas()),
                suma(a.flujoCajaRegistrado(),b.flujoCajaRegistrado()),suma(a.flujoCajaPrevisto(),b.flujoCajaPrevisto()));
    }
    private static Facturacion sumar(Facturacion a,Facturacion b) {
        return new Facturacion(a.facturas()+b.facturas(),suma(a.subtotal(),b.subtotal()),suma(a.descuento(),b.descuento()),suma(a.impuestos(),b.impuestos()),suma(a.total(),b.total()));
    }
    private static Movimientos sumar(Movimientos a,Movimientos b) { return new Movimientos(a.movimientos()+b.movimientos(),suma(a.importe(),b.importe())); }
    private static Saldos sumar(Saldos a,Saldos b) {
        return new Saldos(a.facturas()+b.facturas(),suma(a.totalFacturas(),b.totalFacturas()),suma(a.movimientosRegistrados(),b.movimientosRegistrados()),suma(a.saldoAritmetico(),b.saldoAritmetico()));
    }
    private static String suma(String a,String b) { return decimal(new BigDecimal(a).add(new BigDecimal(b))); }
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
