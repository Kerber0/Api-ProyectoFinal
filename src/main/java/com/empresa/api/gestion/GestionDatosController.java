package com.empresa.api.gestion;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.empresa.api.gestion.GestionDatosDtos.*;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}/gestion/{tipo}")
public class GestionDatosController {
    private final GestionDatosService service;
    public GestionDatosController(GestionDatosService service) { this.service = service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    private TipoTitular tipo(String tipo) {
        return switch(tipo) {
            case "clientes" -> TipoTitular.CLIENTE;
            case "proveedores" -> TipoTitular.PROVEEDOR;
            case "operarios" -> TipoTitular.OPERARIO;
            default -> throw com.empresa.api.common.ApiException.noEncontrado();
        };
    }
    @GetMapping("/{id}/contactos")
    public List<ContactoFicha> contactos(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable String tipo,@PathVariable int id) {
        return service.contactos(usuario(jwt),empresa,tipo(tipo),id);
    }
    @GetMapping("/{id}/contactos/{contacto}")
    public ContactoFicha contacto(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable String tipo,@PathVariable int id,@PathVariable int contacto) {
        return service.contacto(usuario(jwt),empresa,tipo(tipo),id,contacto);
    }
    @PostMapping("/{id}/contactos") @ResponseStatus(HttpStatus.CREATED)
    public ContactoFicha crearContacto(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable String tipo,@PathVariable int id,@Valid @RequestBody ContactoEntrada data) {
        return service.guardarContacto(usuario(jwt),empresa,tipo(tipo),id,null,data);
    }
    @PutMapping("/{id}/contactos/{contacto}")
    public ContactoFicha actualizarContacto(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable String tipo,@PathVariable int id,@PathVariable int contacto,@Valid @RequestBody ContactoEntrada data) {
        return service.guardarContacto(usuario(jwt),empresa,tipo(tipo),id,contacto,data);
    }
    @GetMapping("/{id}/direcciones")
    public List<DireccionFicha> direcciones(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable String tipo,@PathVariable int id) {
        return service.direcciones(usuario(jwt),empresa,tipo(tipo),id);
    }
    @GetMapping("/{id}/direcciones/{direccion}")
    public DireccionFicha direccion(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable String tipo,@PathVariable int id,@PathVariable int direccion) {
        return service.direccion(usuario(jwt),empresa,tipo(tipo),id,direccion);
    }
    @PostMapping("/{id}/direcciones") @ResponseStatus(HttpStatus.CREATED)
    public DireccionFicha crearDireccion(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable String tipo,@PathVariable int id,@Valid @RequestBody DireccionEntrada data) {
        return service.guardarDireccion(usuario(jwt),empresa,tipo(tipo),id,null,data);
    }
    @PutMapping("/{id}/direcciones/{direccion}")
    public DireccionFicha actualizarDireccion(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable String tipo,@PathVariable int id,@PathVariable int direccion,@Valid @RequestBody DireccionEntrada data) {
        return service.guardarDireccion(usuario(jwt),empresa,tipo(tipo),id,direccion,data);
    }
}
