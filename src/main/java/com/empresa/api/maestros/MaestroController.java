package com.empresa.api.maestros;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}")
public class MaestroController {
    private final MaestroService service;
    public MaestroController(MaestroService service) { this.service = service; }

    @GetMapping("/ficha")
    public MaestroDtos.FichaEmpresa ficha(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa) {
        return service.ficha(Integer.parseInt(jwt.getSubject()), empresa);
    }

    @PutMapping("/clientes/{id}/visibilidad/{destino}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void compartirCliente(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                 @PathVariable int id, @PathVariable int destino) {
        service.compartirCliente(Integer.parseInt(jwt.getSubject()), empresa, id, destino);
    }

    @PutMapping("/proveedores/{id}/visibilidad/{destino}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void compartirProveedor(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                   @PathVariable int id, @PathVariable int destino) {
        service.compartirProveedor(Integer.parseInt(jwt.getSubject()), empresa, id, destino);
    }
}
