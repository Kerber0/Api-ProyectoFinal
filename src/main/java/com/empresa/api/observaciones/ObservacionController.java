package com.empresa.api.observaciones;

import com.empresa.api.common.Pagina;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.observaciones.ObservacionDtos.*;

@RestController @RequestMapping("/api/v1/empresas/{empresa}")
public class ObservacionController {
    private final ObservacionService service;
    public ObservacionController(ObservacionService service) { this.service=service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    @GetMapping("/obras/{obra}/observaciones")
    public Pagina<Resumen> listar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int obra,@RequestParam MultiValueMap<String,String> query) {
        return service.listar(usuario(jwt),empresa,obra,false,query);
    }
    @GetMapping("/obras/{obra}/observaciones/{id}")
    public Detalle consultar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int obra,@PathVariable int id) {
        return service.consultar(usuario(jwt),empresa,obra,id,false);
    }
    @PostMapping("/obras/{obra}/observaciones") @ResponseStatus(HttpStatus.CREATED)
    public Detalle crear(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int obra,@Valid @RequestBody Nueva request) {
        return service.crear(usuario(jwt),empresa,obra,false,request);
    }
    @PutMapping("/obras/{obra}/observaciones/{id}/resolucion")
    public Detalle resolver(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int obra,@PathVariable int id,@Valid @RequestBody Resolver request) {
        return service.resolver(usuario(jwt),empresa,obra,id,request);
    }
    @GetMapping("/mi-trabajo/obras/{obra}/observaciones")
    public Pagina<Resumen> listarPropias(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int obra,@RequestParam MultiValueMap<String,String> query) {
        return service.listar(usuario(jwt),empresa,obra,true,query);
    }
    @GetMapping("/mi-trabajo/obras/{obra}/observaciones/{id}")
    public Detalle consultarPropia(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int obra,@PathVariable int id) {
        return service.consultar(usuario(jwt),empresa,obra,id,true);
    }
    @PostMapping("/mi-trabajo/obras/{obra}/observaciones") @ResponseStatus(HttpStatus.CREATED)
    public Detalle crearPropia(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int obra,@Valid @RequestBody Nueva request) {
        return service.crear(usuario(jwt),empresa,obra,true,request);
    }
}
