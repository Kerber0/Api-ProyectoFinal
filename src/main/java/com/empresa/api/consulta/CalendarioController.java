package com.empresa.api.consulta;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.consulta.CalendarioDtos.*;
import com.empresa.api.common.Pagina;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}")
public class CalendarioController {
    private final CalendarioService service;
    public CalendarioController(CalendarioService service) { this.service = service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }

    @GetMapping("/eventos")
    public Pagina<Evento> eventos(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @RequestParam MultiValueMap<String, String> query) {
        return service.eventos(usuario(jwt), empresa, null, query);
    }
    @GetMapping("/eventos/{id}")
    public EventoDetalle evento(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.evento(usuario(jwt), empresa, id);
    }
    @GetMapping("/obras/{id}/eventos")
    public Pagina<Evento> eventosObra(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                      @RequestParam MultiValueMap<String, String> query) {
        return service.eventos(usuario(jwt), empresa, id, query);
    }
    @GetMapping("/tareas")
    public Pagina<Tarea> tareas(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @RequestParam MultiValueMap<String, String> query) {
        return service.tareas(usuario(jwt), empresa, null, null, query);
    }
    @GetMapping("/tareas/{id}")
    public TareaDetalle tarea(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.tarea(usuario(jwt), empresa, id);
    }
    @GetMapping("/obras/{id}/tareas")
    public Pagina<Tarea> tareasObra(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                    @RequestParam MultiValueMap<String, String> query) {
        return service.tareas(usuario(jwt), empresa, id, null, query);
    }
    @GetMapping("/operarios/{id}/tareas")
    public Pagina<Tarea> tareasOperario(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                        @RequestParam MultiValueMap<String, String> query) {
        return service.tareas(usuario(jwt), empresa, null, id, query);
    }
}
