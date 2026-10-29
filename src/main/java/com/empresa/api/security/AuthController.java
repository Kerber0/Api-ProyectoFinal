package com.empresa.api.security;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.Tokens> login(@Valid @RequestBody AuthDtos.Login request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthDtos.Tokens> refresh(@Valid @RequestBody AuthDtos.Refresh request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        service.logout(jwt.getClaimAsString("sid"), Integer.parseInt(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthDtos.Perfil me(@AuthenticationPrincipal Jwt jwt) {
        return service.perfil(Integer.parseInt(jwt.getSubject()));
    }

    @GetMapping("/me/roles")
    public List<GestionUsuariosDtos.Asignacion> roles(@AuthenticationPrincipal Jwt jwt) {
        return service.roles(Integer.parseInt(jwt.getSubject()));
    }
}
