package com.empresa.api.tesoreria;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.tesoreria.PrevisionDtos.*;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}/tesoreria/prevision")
public class PrevisionController {
    private final PrevisionService service;
    public PrevisionController(PrevisionService service) { this.service=service; }

    @GetMapping
    public Prevision prevision(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,
                               @RequestParam MultiValueMap<String,String> query) {
        return service.consultar(Integer.parseInt(jwt.getSubject()),empresa,query);
    }
}
