package com.empresa.api.gestion;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.gestion.GestionContactosObraDtos.*;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}/gestion/obras/{id}/contactos")
public class GestionContactosObraController {
    private final GestionContactosObraService service;
    public GestionContactosObraController(GestionContactosObraService service) { this.service=service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Relacion asignar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@Valid @RequestBody Asignar datos) {
        return service.asignar(usuario(jwt),empresa,id,datos);
    }
    @GetMapping("/{relacion}")
    public Relacion consultar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@PathVariable int relacion) {
        return service.consultar(usuario(jwt),empresa,id,relacion);
    }
    @PutMapping("/{relacion}")
    public Relacion editar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@PathVariable int relacion,@Valid @RequestBody Editar datos) {
        return service.editar(usuario(jwt),empresa,id,relacion,datos);
    }
    @DeleteMapping("/{relacion}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincular(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@PathVariable int relacion) {
        service.desvincular(usuario(jwt),empresa,id,relacion);
    }
}
