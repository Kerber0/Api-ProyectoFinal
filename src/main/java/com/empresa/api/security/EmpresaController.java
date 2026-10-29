package com.empresa.api.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/empresas")
public class EmpresaController {
    private final EmpresaService service;
    public EmpresaController(EmpresaService service) { this.service = service; }

    @GetMapping
    public List<AuthDtos.Empresa> empresas(@AuthenticationPrincipal Jwt jwt) {
        return service.empresas(Integer.parseInt(jwt.getSubject()));
    }

    @GetMapping("/{id}")
    public AuthDtos.Empresa empresa(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        return service.empresa(Integer.parseInt(jwt.getSubject()), id);
    }
}
