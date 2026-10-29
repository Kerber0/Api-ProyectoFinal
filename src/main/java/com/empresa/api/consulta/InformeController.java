package com.empresa.api.consulta;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.consulta.InformeDtos.*;
import static com.empresa.api.consulta.EconomiaTipo.*;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}/informes")
public class InformeController {
    private final InformeService service;
    public InformeController(InformeService service) { this.service = service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }

    @GetMapping("/facturacion-cliente")
    public Informe<Facturacion> facturacionCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @RequestParam MultiValueMap<String, String> query) {
        return service.facturacion(usuario(jwt), empresa, CLIENTE, query);
    }

    @GetMapping("/facturacion-proveedor")
    public Informe<Facturacion> facturacionProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @RequestParam MultiValueMap<String, String> query) {
        return service.facturacion(usuario(jwt), empresa, PROVEEDOR, query);
    }

    @GetMapping("/cobros")
    public Informe<Movimientos> cobros(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @RequestParam MultiValueMap<String, String> query) {
        return service.movimientos(usuario(jwt), empresa, CLIENTE, query);
    }

    @GetMapping("/pagos")
    public Informe<Movimientos> pagos(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @RequestParam MultiValueMap<String, String> query) {
        return service.movimientos(usuario(jwt), empresa, PROVEEDOR, query);
    }

    @GetMapping("/saldos-cliente")
    public Informe<Saldos> saldosCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @RequestParam MultiValueMap<String, String> query) {
        return service.saldos(usuario(jwt), empresa, CLIENTE, query);
    }

    @GetMapping("/saldos-proveedor")
    public Informe<Saldos> saldosProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
            @RequestParam MultiValueMap<String, String> query) {
        return service.saldos(usuario(jwt), empresa, PROVEEDOR, query);
    }

    @GetMapping("/grupo")
    public InformeGrupo grupo(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,
            @RequestParam MultiValueMap<String,String> query) {
        return service.grupo(usuario(jwt),empresa,query);
    }

    @GetMapping("/obras-por-estado")
    public InformeEstados estadosObras(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,
            @RequestParam MultiValueMap<String,String> query) {
        return service.estadosObras(usuario(jwt),empresa,query);
    }

    @GetMapping("/presupuestos-por-estado")
    public InformeEstados estadosPresupuestos(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,
            @RequestParam MultiValueMap<String,String> query) {
        return service.estadosPresupuestos(usuario(jwt),empresa,query);
    }
}
