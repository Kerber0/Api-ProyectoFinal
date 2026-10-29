package com.empresa.api.consulta;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.consulta.IncidenciaDtos.*;
import com.empresa.api.common.Pagina;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}")
public class IncidenciaController {
    private final IncidenciaService service;
    public IncidenciaController(IncidenciaService service) { this.service = service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }

    @GetMapping("/incidencias")
    public Pagina<Resumen> incidencias(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                       @RequestParam MultiValueMap<String, String> query) {
        return service.incidencias(usuario(jwt), empresa, query);
    }
    @GetMapping("/incidencias/{id}")
    public Detalle incidencia(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.incidencia(usuario(jwt), empresa, id);
    }
    @GetMapping("/obras/{id}/incidencias")
    public Pagina<Resumen> obra(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                @RequestParam MultiValueMap<String, String> query) {
        return service.obra(usuario(jwt), empresa, id, query);
    }
    @GetMapping("/operarios/{id}/incidencias")
    public Pagina<Resumen> operario(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                    @RequestParam MultiValueMap<String, String> query) {
        return service.operario(usuario(jwt), empresa, id, query);
    }
}
