package com.empresa.api.consulta;

import com.empresa.api.common.Pagina;

import com.empresa.api.common.ApiException;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.consulta.ConsultaDtos.*;
import static com.empresa.api.consulta.ConsultaParametros.Tipo.*;

@Service
@Transactional(readOnly = true)
public class ConsultaService {
    private final AutorizacionService acceso;
    private final ConsultaRepository repository;
    public ConsultaService(AutorizacionService acceso, ConsultaRepository repository) {
        this.acceso = acceso; this.repository = repository;
    }
    private void autorizar(int usuario, int empresa, String permiso) {
        acceso.exigirLectura(usuario, empresa, permiso);
    }
    public Pagina<ClienteResumen> clientes(int usuario, int empresa, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, "CLIENTES");
        return repository.clientes(empresa, ConsultaParametros.parse(query, CLIENTES));
    }
    public ClienteDetalle cliente(int usuario, int empresa, int id) {
        autorizar(usuario, empresa, "CLIENTES");
        return repository.cliente(empresa, id, acceso.permiteLectura(usuario,empresa,"COBROS")).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<ObraResumen> obras(int usuario, int empresa, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, "OBRAS");
        return repository.obras(empresa, ConsultaParametros.parse(query, OBRAS));
    }
    public ObraDetalle obra(int usuario, int empresa, int id) {
        autorizar(usuario, empresa, "OBRAS");
        return repository.obra(empresa, id).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Contacto> contactos(int usuario, int empresa, int id, MultiValueMap<String, String> query) {
        cliente(usuario, empresa, id);
        return repository.contactos(empresa, id, ConsultaParametros.parse(query, CONTACTOS));
    }
    public Pagina<Direccion> direcciones(int usuario, int empresa, int id, MultiValueMap<String, String> query) {
        cliente(usuario, empresa, id);
        return repository.direcciones(empresa, id, ConsultaParametros.parse(query, DIRECCIONES));
    }
    public Pagina<ContactoObra> contactosObra(int usuario, int empresa, int id, MultiValueMap<String, String> query) {
        obra(usuario, empresa, id);
        autorizar(usuario, empresa, "CLIENTES");
        return repository.contactosObra(empresa, id, ConsultaParametros.parse(query, CONTACTOS_OBRA));
    }
    public ContactoObra contactoObra(int usuario, int empresa, int obra, int relacion) {
        autorizar(usuario, empresa, "EMPRESA");
        autorizar(usuario, empresa, "CLIENTES");
        obra(usuario, empresa, obra);
        return repository.contactoObra(empresa, obra, relacion).orElseThrow(ApiException::noEncontrado);
    }
}
