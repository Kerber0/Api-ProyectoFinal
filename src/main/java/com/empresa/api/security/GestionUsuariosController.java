package com.empresa.api.security;

import com.empresa.api.common.Pagina;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class GestionUsuariosController {
    private final GestionUsuariosService service;
    public GestionUsuariosController(GestionUsuariosService service) { this.service=service; }
    private int autor(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }

    @GetMapping("/usuarios")
    public Pagina<GestionUsuariosDtos.Cuenta> listar(@AuthenticationPrincipal Jwt jwt,@RequestParam MultiValueMap<String,String> query) {
        return service.listar(autor(jwt),query);
    }
    @PostMapping("/usuarios")
    public ResponseEntity<GestionUsuariosDtos.Cuenta> crear(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody GestionUsuariosDtos.Crear request) {
        var cuenta=service.crear(autor(jwt),request);
        return ResponseEntity.created(URI.create("/api/v1/usuarios/"+cuenta.id())).body(cuenta);
    }
    @GetMapping("/usuarios/{id}")
    public GestionUsuariosDtos.Cuenta detalle(@AuthenticationPrincipal Jwt jwt,@PathVariable int id) { return service.detalle(autor(jwt),id); }
    @PutMapping("/usuarios/{id}")
    public GestionUsuariosDtos.Cuenta actualizar(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@Valid @RequestBody GestionUsuariosDtos.Actualizar request) {
        return service.actualizar(autor(jwt),id,request);
    }
    @PutMapping("/usuarios/{id}/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@Valid @RequestBody GestionUsuariosDtos.Password request) {
        service.password(autor(jwt),id,request);
    }
    @GetMapping("/usuarios/{id}/accesos")
    public List<GestionUsuariosDtos.Asignacion> accesos(@AuthenticationPrincipal Jwt jwt,@PathVariable int id) { return service.asignaciones(autor(jwt),id); }
    @PutMapping("/usuarios/{id}/empresas/{empresa}/roles/{rol}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void concederLocal(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@PathVariable int empresa,@PathVariable int rol) {
        service.asignar(autor(jwt),id,empresa,rol,true);
    }
    @DeleteMapping("/usuarios/{id}/empresas/{empresa}/roles/{rol}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocarLocal(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@PathVariable int empresa,@PathVariable int rol) {
        service.asignar(autor(jwt),id,empresa,rol,false);
    }
    @PutMapping("/usuarios/{id}/roles-globales/{rol}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void concederGlobal(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@PathVariable int rol) {
        service.asignar(autor(jwt),id,null,rol,true);
    }
    @DeleteMapping("/usuarios/{id}/roles-globales/{rol}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocarGlobal(@AuthenticationPrincipal Jwt jwt,@PathVariable int id,@PathVariable int rol) {
        service.asignar(autor(jwt),id,null,rol,false);
    }
    @GetMapping("/roles")
    public List<GestionUsuariosDtos.Rol> roles(@AuthenticationPrincipal Jwt jwt) { return service.roles(autor(jwt)); }
    @GetMapping("/permisos")
    public List<GestionUsuariosDtos.Permiso> permisos(@AuthenticationPrincipal Jwt jwt) { return service.permisos(autor(jwt)); }
    @GetMapping("/empresas/{empresa}/permisos")
    public List<GestionUsuariosDtos.Nivel> efectivos(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa) { return service.efectivos(autor(jwt),empresa); }
}
