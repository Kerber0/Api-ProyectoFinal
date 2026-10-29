package com.empresa.api.consulta;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.consulta.AlbaranDtos.*;
import static com.empresa.api.consulta.AlbaranTipo.*;
import com.empresa.api.common.Pagina;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}")
public class AlbaranController {
    private final AlbaranService service;
    public AlbaranController(AlbaranService service) { this.service = service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }

    @GetMapping("/albaranes-cliente")
    public Pagina<Albaran> clientes(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                      @RequestParam MultiValueMap<String, String> query) {
        return service.albaranes(usuario(jwt), empresa, CLIENTE, null, query);
    }
    @GetMapping("/albaranes-cliente/{id}")
    public Albaran cliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.albaran(usuario(jwt), empresa, CLIENTE, id);
    }
    @GetMapping("/albaranes-cliente/{id}/lineas")
    public Pagina<Linea> lineasCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                         @RequestParam MultiValueMap<String, String> query) {
        return service.lineas(usuario(jwt), empresa, CLIENTE, id, query);
    }
    @GetMapping("/albaranes-cliente/{albaran}/lineas/{id}")
    public Linea lineaCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int albaran, @PathVariable int id) {
        return service.linea(usuario(jwt), empresa, CLIENTE, albaran, id);
    }
    @GetMapping("/facturas-cliente/{id}/albaranes")
    public Pagina<Albaran> facturaCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                  @RequestParam MultiValueMap<String, String> query) {
        return service.albaranes(usuario(jwt), empresa, CLIENTE, id, query);
    }
    @GetMapping("/albaranes-proveedor")
    public Pagina<Albaran> proveedores(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                         @RequestParam MultiValueMap<String, String> query) {
        return service.albaranes(usuario(jwt), empresa, PROVEEDOR, null, query);
    }
    @GetMapping("/albaranes-proveedor/{id}")
    public Albaran proveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.albaran(usuario(jwt), empresa, PROVEEDOR, id);
    }
    @GetMapping("/albaranes-proveedor/{id}/lineas")
    public Pagina<Linea> lineasProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                           @RequestParam MultiValueMap<String, String> query) {
        return service.lineas(usuario(jwt), empresa, PROVEEDOR, id, query);
    }
    @GetMapping("/albaranes-proveedor/{albaran}/lineas/{id}")
    public Linea lineaProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int albaran, @PathVariable int id) {
        return service.linea(usuario(jwt), empresa, PROVEEDOR, albaran, id);
    }
    @GetMapping("/facturas-proveedor/{id}/albaranes")
    public Pagina<Albaran> facturaProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                    @RequestParam MultiValueMap<String, String> query) {
        return service.albaranes(usuario(jwt), empresa, PROVEEDOR, id, query);
    }
}
