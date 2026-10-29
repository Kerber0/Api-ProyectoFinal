package com.empresa.api.auditoria;

import com.empresa.api.common.Pagina;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.auditoria.AuditoriaDtos.Evento;

@RestController
@RequestMapping("/api/v1/auditoria")
public class AuditoriaController {
    private final AuditoriaService service;
    public AuditoriaController(AuditoriaService service) { this.service=service; }
    @GetMapping
    public Pagina<Evento> listar(@AuthenticationPrincipal Jwt jwt,@RequestParam MultiValueMap<String,String> query) {
        return service.listar(Integer.parseInt(jwt.getSubject()),query);
    }
    @GetMapping("/{id}")
    public Evento detalle(@AuthenticationPrincipal Jwt jwt,@PathVariable long id) {
        return service.detalle(Integer.parseInt(jwt.getSubject()),id);
    }
}
