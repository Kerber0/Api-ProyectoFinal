package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.consulta.EconomiaDtos.*;
import static com.empresa.api.consulta.ConsultaParametros.Tipo.*;
import com.empresa.api.common.Pagina;

@Service
@Transactional(readOnly = true)
public class EconomiaService {
    private final AutorizacionService acceso;
    private final EconomiaRepository repository;
    public EconomiaService(AutorizacionService acceso, EconomiaRepository repository) {
        this.acceso = acceso; this.repository = repository;
    }
    private void autorizar(int usuario, int empresa, EconomiaTipo tipo, boolean movimientos) {
        acceso.exigirLectura(usuario, empresa, tipo.permisoFactura);
        if (movimientos) { acceso.exigirLectura(usuario, empresa, tipo.permisoMovimiento); }
    }
    public Pagina<Factura> facturas(int usuario, int empresa, EconomiaTipo tipo, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, tipo, false);
        return repository.facturas(empresa, tipo, EconomiaParametros.parse(query, tipo, FACTURAS, false));
    }
    public Factura factura(int usuario, int empresa, EconomiaTipo tipo, int id) {
        autorizar(usuario, empresa, tipo, false);
        return repository.factura(empresa, tipo, id).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Linea> lineas(int usuario, int empresa, EconomiaTipo tipo, int id, MultiValueMap<String, String> query) {
        factura(usuario, empresa, tipo, id);
        return repository.lineas(empresa, tipo, id, ConsultaParametros.parse(query, LINEAS));
    }
    public Linea linea(int usuario, int empresa, EconomiaTipo tipo, int id, int linea) {
        factura(usuario, empresa, tipo, id);
        return repository.linea(empresa, tipo, id, linea).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Movimiento> movimientos(int usuario, int empresa, EconomiaTipo tipo, Integer factura, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, tipo, true);
        if (factura != null) { factura(usuario, empresa, tipo, factura); }
        return repository.movimientos(empresa, tipo, factura, EconomiaParametros.parse(query, tipo, MOVIMIENTOS, factura != null));
    }
    public Movimiento movimiento(int usuario, int empresa, EconomiaTipo tipo, int id) {
        autorizar(usuario, empresa, tipo, true);
        return repository.movimiento(empresa, tipo, id).orElseThrow(ApiException::noEncontrado);
    }
    public Saldo saldo(int usuario, int empresa, EconomiaTipo tipo, int id) {
        autorizar(usuario, empresa, tipo, true);
        return repository.saldo(empresa, tipo, id).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Vencimiento> vencimientos(int usuario, int empresa, EconomiaTipo tipo, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, tipo, true);
        return repository.vencimientos(empresa, tipo, EconomiaParametros.parse(query, tipo, VENCIMIENTOS, false));
    }
}
