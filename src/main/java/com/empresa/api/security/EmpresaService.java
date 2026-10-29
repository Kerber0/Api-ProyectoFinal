package com.empresa.api.security;

import com.empresa.api.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class EmpresaService {
    private final AccesoRepository repository;

    public EmpresaService(AccesoRepository repository) { this.repository = repository; }

    public List<AuthDtos.Empresa> empresas(int usuario) { return repository.empresas(usuario); }

    public AuthDtos.Empresa empresa(int usuario, int id) {
        return repository.empresa(usuario, id).orElseThrow(ApiException::noEncontrado);
    }
}
