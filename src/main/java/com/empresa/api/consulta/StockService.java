package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.consulta.StockDtos.*;
import static com.empresa.api.consulta.ConsultaParametros.Tipo.*;
import com.empresa.api.common.Pagina;

@Service
@Transactional(readOnly = true)
public class StockService {
    private final AutorizacionService acceso;
    private final StockRepository repository;
    public StockService(AutorizacionService acceso, StockRepository repository) {
        this.acceso = acceso; this.repository = repository;
    }
    private void autorizar(int usuario, int empresa, String permiso) {
        acceso.exigirLectura(usuario, empresa, permiso);
    }
    private void autorizarStock(int usuario, int empresa) {
        autorizar(usuario, empresa, "ARTICULOS");
        autorizar(usuario, empresa, "ALMACENES");
        autorizar(usuario, empresa, "STOCK");
    }
    public Pagina<Articulo> articulos(int usuario, int empresa, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, "ARTICULOS");
        return repository.articulos(empresa, ConsultaParametros.parse(query, ARTICULOS));
    }
    public Articulo articulo(int usuario, int empresa, String referencia) {
        autorizar(usuario, empresa, "ARTICULOS");
        return repository.articulo(empresa, referencia).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Almacen> almacenes(int usuario, int empresa, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, "ALMACENES");
        return repository.almacenes(empresa, ConsultaParametros.parse(query, ALMACENES));
    }
    public Almacen almacen(int usuario, int empresa, int id) {
        autorizar(usuario, empresa, "ALMACENES");
        return repository.almacen(empresa, id).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Existencia> stock(int usuario, int empresa, int id, MultiValueMap<String, String> query) {
        autorizarStock(usuario, empresa);
        almacen(usuario, empresa, id);
        return repository.stock(empresa, id, ConsultaParametros.parse(query, STOCK));
    }
    public Pagina<Movimiento> movimientos(int usuario, int empresa, Integer almacen, MultiValueMap<String, String> query) {
        autorizarStock(usuario, empresa);
        if (almacen != null) { almacen(usuario, empresa, almacen); }
        return repository.movimientos(empresa, almacen, ConsultaParametros.parse(query, MOVIMIENTOS_STOCK));
    }
    public Movimiento movimiento(int usuario, int empresa, int id) {
        autorizarStock(usuario, empresa);
        return repository.movimiento(empresa, id).orElseThrow(ApiException::noEncontrado);
    }
}
