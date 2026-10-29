package com.empresa.api.consulta;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.consulta.CompraDtos.*;
import com.empresa.api.common.Pagina;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}")
public class CompraController {
    private final CompraService service;
    public CompraController(CompraService service) { this.service = service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }

    @GetMapping("/proveedores")
    public Pagina<Proveedor> proveedores(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                          @RequestParam MultiValueMap<String, String> query) {
        return service.proveedores(usuario(jwt), empresa, query);
    }
    @GetMapping("/proveedores/{id}")
    public ProveedorDetalle proveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.proveedor(usuario(jwt), empresa, id);
    }
    @GetMapping("/proveedores/{id}/contactos")
    public Pagina<ConsultaDtos.Contacto> contactos(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestParam MultiValueMap<String,String> query) {
        return service.contactos(usuario(jwt),empresa,id,query);
    }
    @GetMapping("/proveedores/{id}/direcciones")
    public Pagina<ConsultaDtos.Direccion> direcciones(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestParam MultiValueMap<String,String> query) {
        return service.direcciones(usuario(jwt),empresa,id,query);
    }
    @GetMapping("/proveedores/{id}/pedidos")
    public Pagina<Pedido> pedidosProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                            @RequestParam MultiValueMap<String, String> query) {
        return service.pedidos(usuario(jwt), empresa, id, query);
    }
    @GetMapping("/pedidos-proveedor")
    public Pagina<Pedido> pedidos(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                   @RequestParam MultiValueMap<String, String> query) {
        return service.pedidos(usuario(jwt), empresa, null, query);
    }
    @GetMapping("/pedidos-proveedor/{id}")
    public Pedido pedido(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.pedido(usuario(jwt), empresa, id);
    }
    @GetMapping("/pedidos-proveedor/{id}/lineas")
    public Pagina<Linea> lineas(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                 @RequestParam MultiValueMap<String, String> query) {
        return service.lineas(usuario(jwt), empresa, id, query);
    }
    @GetMapping("/pedidos-proveedor/{pedido}/lineas/{id}")
    public Linea linea(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int pedido, @PathVariable int id) {
        return service.linea(usuario(jwt), empresa, pedido, id);
    }
}
