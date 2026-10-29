package com.empresa.api.consulta;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.consulta.LaboralDtos.*;
import com.empresa.api.common.Pagina;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}")
public class LaboralController {
    private final LaboralService service;
    public LaboralController(LaboralService service) { this.service = service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }

    @GetMapping("/operarios")
    public Pagina<Operario> operarios(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                       @RequestParam MultiValueMap<String, String> query) {
        return service.operarios(usuario(jwt), empresa, query);
    }
    @GetMapping("/operarios/{id}")
    public Operario operario(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.operario(usuario(jwt), empresa, id);
    }
    @GetMapping("/partes-mano-obra")
    public Pagina<Parte> partes(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                  @RequestParam MultiValueMap<String, String> query) {
        return service.partes(usuario(jwt), empresa, query);
    }
    @GetMapping("/partes-mano-obra/{id}")
    public Parte parte(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.parte(usuario(jwt), empresa, id);
    }
    @GetMapping("/operarios/{id}/partes")
    public Pagina<Parte> partesOperario(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                          @RequestParam MultiValueMap<String, String> query) {
        return service.partesOperario(usuario(jwt), empresa, id, query);
    }
    @GetMapping("/obras/{id}/partes")
    public Pagina<Parte> partesObra(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                      @RequestParam MultiValueMap<String, String> query) {
        return service.partesObra(usuario(jwt), empresa, id, query);
    }
}
