package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.consulta.LaboralDtos.*;
import static com.empresa.api.consulta.ConsultaParametros.Tipo.*;
import com.empresa.api.common.Pagina;

@Service
@Transactional(readOnly = true)
public class LaboralService {
    private final AutorizacionService acceso;
    private final LaboralRepository repository;
    private final ConsultaService consultas;
    public LaboralService(AutorizacionService acceso, LaboralRepository repository, ConsultaService consultas) {
        this.acceso = acceso; this.repository = repository; this.consultas = consultas;
    }
    private void autorizar(int usuario, int empresa, String permiso) {
        acceso.exigirLectura(usuario, empresa, permiso);
    }
    private void autorizarPartes(int usuario, int empresa) {
        autorizar(usuario, empresa, "OPERARIOS");
        autorizar(usuario, empresa, "PARTES_MANO_OBRA");
        autorizar(usuario, empresa, "OBRAS");
    }
    public Pagina<Operario> operarios(int usuario, int empresa, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, "OPERARIOS");
        return repository.operarios(empresa, ConsultaParametros.parse(query, OPERARIOS));
    }
    public Operario operario(int usuario, int empresa, int id) {
        autorizar(usuario, empresa, "OPERARIOS");
        return repository.operario(empresa, id).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Parte> partes(int usuario, int empresa, MultiValueMap<String, String> query) {
        autorizarPartes(usuario, empresa);
        var p = ConsultaParametros.parse(query, PARTES);
        return repository.partes(empresa, p.obra(), p.operario(), p);
    }
    public Parte parte(int usuario, int empresa, int id) {
        autorizarPartes(usuario, empresa);
        return repository.parte(empresa, id).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Parte> partesOperario(int usuario, int empresa, int id, MultiValueMap<String, String> query) {
        autorizarPartes(usuario, empresa);
        operario(usuario, empresa, id);
        var p = ConsultaParametros.parse(query, PARTES_OPERARIO);
        return repository.partes(empresa, p.obra(), id, p);
    }
    public Pagina<Parte> partesObra(int usuario, int empresa, int id, MultiValueMap<String, String> query) {
        autorizarPartes(usuario, empresa);
        consultas.obra(usuario, empresa, id);
        var p = ConsultaParametros.parse(query, PARTES_OBRA);
        return repository.partes(empresa, id, p.operario(), p);
    }
}
