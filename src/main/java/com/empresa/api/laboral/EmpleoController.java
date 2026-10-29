package com.empresa.api.laboral;

import com.empresa.api.common.Pagina;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.laboral.EmpleoDtos.*;

@RestController @RequestMapping("/api/v1/empresas/{empresa}")
public class EmpleoController {
    private final EmpleoService service;
    public EmpleoController(EmpleoService service) { this.service=service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    @GetMapping("/operarios/{id}/usuario")
    public Vinculo vinculo(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id) {
        return service.vinculo(usuario(jwt),empresa,id);
    }
    @PutMapping("/operarios/{id}/usuario") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void vincular(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@Valid @RequestBody Vincular request) {
        service.vincular(usuario(jwt),empresa,id,request);
    }
    @DeleteMapping("/operarios/{id}/usuario") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desvincular(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id) {
        service.desvincular(usuario(jwt),empresa,id);
    }
    @GetMapping("/operarios/{id}/asignaciones")
    public Pagina<Asignacion> asignaciones(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestParam MultiValueMap<String,String> query) {
        return service.asignaciones(usuario(jwt),empresa,id,query);
    }
    @PostMapping("/operarios/{id}/asignaciones") @ResponseStatus(HttpStatus.CREATED)
    public Asignacion asignar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@Valid @RequestBody NuevaAsignacion request) {
        return service.asignar(usuario(jwt),empresa,id,request);
    }
    @DeleteMapping("/operarios/{id}/asignaciones/{asignacion}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarAsignacion(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@PathVariable int asignacion) {
        service.cancelarAsignacion(usuario(jwt),empresa,id,asignacion);
    }
    @GetMapping("/operarios/{id}/ausencias")
    public Pagina<AusenciaDetallePrivado> ausencias(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestParam MultiValueMap<String,String> query) {
        return service.ausencias(usuario(jwt),empresa,id,query);
    }
    @PostMapping("/operarios/{id}/ausencias") @ResponseStatus(HttpStatus.CREATED)
    public AusenciaDetallePrivado ausencia(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@Valid @RequestBody NuevaAusencia request) {
        return service.ausencia(usuario(jwt),empresa,id,request);
    }
    @DeleteMapping("/operarios/{id}/ausencias/{ausencia}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelarAusencia(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@PathVariable int ausencia) {
        service.cancelarAusencia(usuario(jwt),empresa,id,ausencia);
    }
    @GetMapping("/mi-trabajo/empleado")
    public Empleado empleado(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa) { return service.empleado(usuario(jwt),empresa); }
    @GetMapping("/mi-trabajo/obras")
    public Pagina<ObraOperativa> obras(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@RequestParam MultiValueMap<String,String> query) {
        return service.obras(usuario(jwt),empresa,query);
    }
    @GetMapping("/mi-trabajo/obras/{obra}")
    public ObraOperativa obra(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int obra) { return service.obra(usuario(jwt),empresa,obra); }
    @GetMapping("/mi-trabajo/obras/{obra}/contactos")
    public Pagina<ContactoOperativo> contactos(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int obra,@RequestParam MultiValueMap<String,String> query) {
        return service.contactos(usuario(jwt),empresa,obra,query);
    }
    @GetMapping("/mi-trabajo/ausencias")
    public Pagina<AusenciaDetalle> ausenciasPropias(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@RequestParam MultiValueMap<String,String> query) {
        return service.ausenciasPropias(usuario(jwt),empresa,query);
    }
}