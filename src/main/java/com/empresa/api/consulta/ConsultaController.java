package com.empresa.api.consulta;

import com.empresa.api.common.Pagina;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.consulta.ConsultaDtos.*;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}")
public class ConsultaController {
    private final ConsultaService service;
    public ConsultaController(ConsultaService service) { this.service = service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }

    @GetMapping("/clientes")
    public Pagina<ClienteResumen> clientes(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                           @RequestParam MultiValueMap<String, String> query) {
        return service.clientes(usuario(jwt), empresa, query);
    }
    @GetMapping("/clientes/{id}")
    public ClienteDetalle cliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.cliente(usuario(jwt), empresa, id);
    }
    @GetMapping("/clientes/{id}/contactos")
    public Pagina<Contacto> contactos(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                      @RequestParam MultiValueMap<String, String> query) {
        return service.contactos(usuario(jwt), empresa, id, query);
    }
    @GetMapping("/clientes/{id}/direcciones")
    public Pagina<Direccion> direcciones(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                         @RequestParam MultiValueMap<String, String> query) {
        return service.direcciones(usuario(jwt), empresa, id, query);
    }
    @GetMapping("/obras")
    public Pagina<ObraResumen> obras(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                     @RequestParam MultiValueMap<String, String> query) {
        return service.obras(usuario(jwt), empresa, query);
    }
    @GetMapping("/obras/{id}")
    public ObraDetalle obra(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.obra(usuario(jwt), empresa, id);
    }
    @GetMapping("/obras/{id}/contactos")
    public Pagina<ContactoObra> contactosObra(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                              @RequestParam MultiValueMap<String, String> query) {
        return service.contactosObra(usuario(jwt), empresa, id, query);
    }
}
