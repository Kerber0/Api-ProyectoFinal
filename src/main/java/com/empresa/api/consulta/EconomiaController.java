package com.empresa.api.consulta;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.consulta.EconomiaDtos.*;
import static com.empresa.api.consulta.EconomiaTipo.*;
import com.empresa.api.common.Pagina;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}")
public class EconomiaController {
    private final EconomiaService service;
    public EconomiaController(EconomiaService service) { this.service = service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }

    @GetMapping("/facturas-cliente")
    public Pagina<Factura> facturasCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @RequestParam MultiValueMap<String, String> query) {
        return service.facturas(usuario(jwt), empresa, CLIENTE, query);
    }

    @GetMapping("/facturas-cliente/{id}")
    public Factura facturaCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @PathVariable int id) {
        return service.factura(usuario(jwt), empresa, CLIENTE, id);
    }

    @GetMapping("/facturas-cliente/{id}/lineas")
    public Pagina<Linea> lineasCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @PathVariable int id, @RequestParam MultiValueMap<String, String> query) {
        return service.lineas(usuario(jwt), empresa, CLIENTE, id, query);
    }

    @GetMapping("/facturas-cliente/{id}/lineas/{linea}")
    public Linea lineaCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @PathVariable int id, @PathVariable int linea) {
        return service.linea(usuario(jwt), empresa, CLIENTE, id, linea);
    }

    @GetMapping("/facturas-cliente/{id}/cobros")
    public Pagina<Movimiento> cobrosFactura(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @PathVariable int id, @RequestParam MultiValueMap<String, String> query) {
        return service.movimientos(usuario(jwt), empresa, CLIENTE, id, query);
    }

    @GetMapping("/facturas-cliente/{id}/saldo")
    public Saldo saldoCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @PathVariable int id) {
        return service.saldo(usuario(jwt), empresa, CLIENTE, id);
    }

    @GetMapping("/cobros")
    public Pagina<Movimiento> cobros(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @RequestParam MultiValueMap<String, String> query) {
        return service.movimientos(usuario(jwt), empresa, CLIENTE, null, query);
    }

    @GetMapping("/cobros/{id}")
    public Movimiento cobrosDetalle(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @PathVariable int id) {
        return service.movimiento(usuario(jwt), empresa, CLIENTE, id);
    }

    @GetMapping("/vencimientos-cliente")
    public Pagina<Vencimiento> vencimientosCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @RequestParam MultiValueMap<String, String> query) {
        return service.vencimientos(usuario(jwt), empresa, CLIENTE, query);
    }

    @GetMapping("/facturas-proveedor")
    public Pagina<Factura> facturasProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @RequestParam MultiValueMap<String, String> query) {
        return service.facturas(usuario(jwt), empresa, PROVEEDOR, query);
    }

    @GetMapping("/facturas-proveedor/{id}")
    public Factura facturaProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @PathVariable int id) {
        return service.factura(usuario(jwt), empresa, PROVEEDOR, id);
    }

    @GetMapping("/facturas-proveedor/{id}/lineas")
    public Pagina<Linea> lineasProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @PathVariable int id, @RequestParam MultiValueMap<String, String> query) {
        return service.lineas(usuario(jwt), empresa, PROVEEDOR, id, query);
    }

    @GetMapping("/facturas-proveedor/{id}/lineas/{linea}")
    public Linea lineaProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @PathVariable int id, @PathVariable int linea) {
        return service.linea(usuario(jwt), empresa, PROVEEDOR, id, linea);
    }

    @GetMapping("/facturas-proveedor/{id}/pagos")
    public Pagina<Movimiento> pagosFactura(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @PathVariable int id, @RequestParam MultiValueMap<String, String> query) {
        return service.movimientos(usuario(jwt), empresa, PROVEEDOR, id, query);
    }

    @GetMapping("/facturas-proveedor/{id}/saldo")
    public Saldo saldoProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @PathVariable int id) {
        return service.saldo(usuario(jwt), empresa, PROVEEDOR, id);
    }

    @GetMapping("/pagos")
    public Pagina<Movimiento> pagos(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @RequestParam MultiValueMap<String, String> query) {
        return service.movimientos(usuario(jwt), empresa, PROVEEDOR, null, query);
    }

    @GetMapping("/pagos/{id}")
    public Movimiento pagosDetalle(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @PathVariable int id) {
        return service.movimiento(usuario(jwt), empresa, PROVEEDOR, id);
    }

    @GetMapping("/vencimientos-proveedor")
    public Pagina<Vencimiento> vencimientosProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @RequestParam MultiValueMap<String, String> query) {
        return service.vencimientos(usuario(jwt), empresa, PROVEEDOR, query);
    }
}
