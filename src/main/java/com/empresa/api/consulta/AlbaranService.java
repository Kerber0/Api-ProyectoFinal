package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.consulta.AlbaranDtos.*;
import com.empresa.api.common.Pagina;

@Service
@Transactional(readOnly = true)
public class AlbaranService {
    private final AutorizacionService acceso;
    private final AlbaranRepository repository;
    private final EconomiaService economia;
    public AlbaranService(AutorizacionService acceso, AlbaranRepository repository, EconomiaService economia) {
        this.acceso = acceso; this.repository = repository; this.economia = economia;
    }
    private void permiso(int usuario, int empresa, String permiso) {
        acceso.exigirLectura(usuario, empresa, permiso);
    }
    private void autorizar(int usuario, int empresa, AlbaranTipo tipo) {
        permiso(usuario, empresa, tipo.permiso);
        permiso(usuario, empresa, tipo.permisoTercero);
        permiso(usuario, empresa, tipo.permisoRelacion);
    }
    public Pagina<Albaran> albaranes(int usuario, int empresa, AlbaranTipo tipo, Integer relacion, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, tipo);
        if (relacion != null) {
            economia.factura(usuario, empresa, tipo.economia(), relacion);
        }
        return repository.albaranes(empresa, tipo, relacion, AlbaranParametros.parse(query, tipo, relacion != null));
    }
    public Albaran albaran(int usuario, int empresa, AlbaranTipo tipo, int id) {
        autorizar(usuario, empresa, tipo);
        return repository.albaran(empresa, tipo, id).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Linea> lineas(int usuario, int empresa, AlbaranTipo tipo, int id, MultiValueMap<String, String> query) {
        permiso(usuario, empresa, "ARTICULOS");
        albaran(usuario, empresa, tipo, id);
        return repository.lineas(empresa, tipo, id, ConsultaParametros.parse(query, ConsultaParametros.Tipo.LINEAS));
    }
    public Linea linea(int usuario, int empresa, AlbaranTipo tipo, int albaran, int id) {
        permiso(usuario, empresa, "ARTICULOS");
        albaran(usuario, empresa, tipo, albaran);
        return repository.linea(empresa, tipo, albaran, id).orElseThrow(ApiException::noEncontrado);
    }
}
