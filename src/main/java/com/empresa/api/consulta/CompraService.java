package com.empresa.api.consulta;

import com.empresa.api.common.ApiException;
import com.empresa.api.security.AutorizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import static com.empresa.api.consulta.CompraDtos.*;
import static com.empresa.api.consulta.ConsultaParametros.Tipo.*;
import com.empresa.api.common.Pagina;

@Service
@Transactional(readOnly = true)
public class CompraService {
    private final AutorizacionService acceso;
    private final CompraRepository repository;
    private final ConsultaRepository consulta;
    public CompraService(AutorizacionService acceso, CompraRepository repository, ConsultaRepository consulta) {
        this.acceso = acceso; this.repository = repository; this.consulta=consulta;
    }
    private void autorizar(int usuario, int empresa, String permiso) {
        acceso.exigirLectura(usuario, empresa, permiso);
    }
    private void autorizarPedidos(int usuario, int empresa) {
        autorizar(usuario, empresa, "PROVEEDORES");
        autorizar(usuario, empresa, "PEDIDOS");
    }
    public Pagina<Proveedor> proveedores(int usuario, int empresa, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, "PROVEEDORES");
        return repository.proveedores(empresa, ConsultaParametros.parse(query, PROVEEDORES));
    }
    public ProveedorDetalle proveedor(int usuario, int empresa, int id) {
        autorizar(usuario, empresa, "PROVEEDORES");
        return repository.proveedor(empresa, id, acceso.permiteLectura(usuario,empresa,"PAGOS")).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<com.empresa.api.consulta.ConsultaDtos.Contacto> contactos(int usuario,int empresa,int id,MultiValueMap<String,String> q) {
        proveedor(usuario,empresa,id); return consulta.contactosProveedor(empresa,id,ConsultaParametros.parse(q,CONTACTOS));
    }
    public Pagina<com.empresa.api.consulta.ConsultaDtos.Direccion> direcciones(int usuario,int empresa,int id,MultiValueMap<String,String> q) {
        proveedor(usuario,empresa,id); return consulta.direccionesProveedor(empresa,id,ConsultaParametros.parse(q,DIRECCIONES));
    }
    public Pagina<Pedido> pedidos(int usuario, int empresa, Integer proveedor, MultiValueMap<String, String> query) {
        autorizarPedidos(usuario, empresa);
        if (proveedor != null) { proveedor(usuario, empresa, proveedor); }
        return repository.pedidos(empresa, proveedor, CompraParametros.parse(query, proveedor != null));
    }
    public Pedido pedido(int usuario, int empresa, int id) {
        autorizarPedidos(usuario, empresa);
        return repository.pedido(empresa, id).orElseThrow(ApiException::noEncontrado);
    }
    public Pagina<Linea> lineas(int usuario, int empresa, int id, MultiValueMap<String, String> query) {
        autorizar(usuario, empresa, "ARTICULOS");
        pedido(usuario, empresa, id);
        return repository.lineas(empresa, id, ConsultaParametros.parse(query, LINEAS));
    }
    public Linea linea(int usuario, int empresa, int pedido, int id) {
        autorizar(usuario, empresa, "ARTICULOS");
        pedido(usuario, empresa, pedido);
        return repository.linea(empresa, pedido, id).orElseThrow(ApiException::noEncontrado);
    }
}
