package com.empresa.api.consulta;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.consulta.DashboardDtos.*;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}/dashboard")
public class DashboardController {
    private final DashboardService service;
    public DashboardController(DashboardService service) { this.service = service; }

    @GetMapping
    public Resumen consultar(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                             @RequestParam MultiValueMap<String, String> query) {
        return service.consultar(Integer.parseInt(jwt.getSubject()), empresa, query);
    }
}
