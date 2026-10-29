package com.empresa.api.security;

import com.empresa.api.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Los servicios de negocio declaran los permisos; este servicio aplica la politica vigente. */
@Service
@Transactional(readOnly = true)
public class AutorizacionService {
    private final AccesoRepository repository;

    public AutorizacionService(AccesoRepository repository) { this.repository = repository; }

    public void exigirLectura(int usuario, int empresa, String permiso) {
        if (!repository.permiteLectura(usuario, empresa, permiso)) { throw ApiException.noEncontrado(); }
    }

    public void exigirEscritura(int usuario, int empresa, String permiso) {
        if (!repository.permiteEscritura(usuario, empresa, permiso)) { throw ApiException.noEncontrado(); }
    }

    public boolean permiteLectura(int usuario, int empresa, String permiso) {
        return repository.permiteLectura(usuario, empresa, permiso);
    }
}
