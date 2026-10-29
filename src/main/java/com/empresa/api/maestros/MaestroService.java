package com.empresa.api.maestros;

import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MaestroService {
    private final MaestroRepository repository;
    private final AutorizacionService autorizacion;
    public MaestroService(MaestroRepository repository, AutorizacionService autorizacion) {
        this.repository = repository; this.autorizacion = autorizacion;
    }

    public MaestroDtos.FichaEmpresa ficha(int usuario, int empresa) {
        autorizacion.exigirLectura(usuario, empresa, "EMPRESA");
        var e = repository.empresa(empresa);
        var fiscal = e.getEntidad();
        return new MaestroDtos.FichaEmpresa(e.getId(), e.getNombre(), fiscal == null ? null :
                new MaestroDtos.DatosFiscales(fiscal.getRazonSocial(), fiscal.getNifCif(), fiscal.getTelefonoFijo(), fiscal.getEmail(), fiscal.getWeb()));
    }

    @Transactional
    public void compartirCliente(int usuario, int origen, int id, int destino) { compartir(usuario, origen, id, destino, true); }

    @Transactional
    public void compartirProveedor(int usuario, int origen, int id, int destino) { compartir(usuario, origen, id, destino, false); }

    private void compartir(int usuario, int origen, int id, int destino, boolean cliente) {
        String permiso = cliente ? "CLIENTES" : "PROVEEDORES";
        for (int empresa : new int[]{origen, destino}) {
            autorizacion.exigirLectura(usuario, empresa, "EMPRESA");
            autorizacion.exigirEscritura(usuario, empresa, permiso);
        }
        var empresa = repository.empresa(destino);
        repository.serializar();
        for (int ambito : new int[]{origen, destino}) {
            autorizacion.exigirLectura(usuario, ambito, "EMPRESA");
            autorizacion.exigirEscritura(usuario, ambito, permiso);
        }
        var visibles = cliente ? repository.visibilidadCliente(id, origen) : repository.visibilidadProveedor(id, origen);
        if (visibles.stream().noneMatch(e -> e.getId() == destino)) {
            visibles.add(empresa);
            repository.registrar(usuario, origen, destino, cliente ? id : null, cliente ? null : id);
        }
    }
}
