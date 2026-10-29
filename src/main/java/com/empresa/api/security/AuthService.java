package com.empresa.api.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    private final AccesoRepository repository;
    private final GestionUsuariosRepository usuarios;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(AccesoRepository repository, GestionUsuariosRepository usuarios,
                       PasswordEncoder passwords, TokenService tokens, Clock clock) {
        this.repository = repository;
        this.usuarios = usuarios;
        this.passwords = passwords;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthDtos.Tokens login(AuthDtos.Login request) {
        var cuenta = repository.bloquearCuenta(request.usuario()).orElse(null);
        boolean correcta;
        try {
            correcta = passwords.matches(request.password(), cuenta == null ? dummyHash : cuenta.hash());
        } catch (IllegalArgumentException ex) {
            correcta = false;
        }
        if (!correcta || cuenta == null || !cuenta.activa()) { throw denegado(); }
        String sesion = UUID.randomUUID().toString();
        String refresh = tokens.nuevoRefresh(sesion);
        var expira = clock.instant().plus(TokenService.SESSION_TTL);
        repository.crearSesion(sesion, cuenta.id(), TokenService.hash(refresh), expira);
        return tokens.emitir(cuenta.id(), sesion, 0, refresh, expira);
    }

    @Transactional
    public AuthDtos.Tokens refresh(String refresh) {
        if (!refresh.matches("[a-f0-9-]{36}\\.[A-Za-z0-9_-]{43}")) { throw denegado(); }
        String id = refresh.substring(0, 36);
        // El bloqueo serializa renovaciones simultaneas del mismo token.
        var sesion = repository.bloquearSesion(id).orElseThrow(AuthService::denegado);
        if (sesion.revocada() || !sesion.expira().isAfter(clock.instant()) ||
                !MessageDigest.isEqual(TokenService.hash(refresh).getBytes(StandardCharsets.US_ASCII),
                        sesion.hash().getBytes(StandardCharsets.US_ASCII))) { throw denegado(); }
        String nuevoRefresh = tokens.nuevoRefresh(id);
        repository.rotar(id, TokenService.hash(nuevoRefresh));
        return tokens.emitir(sesion.usuario(), id, sesion.version() + 1, nuevoRefresh, sesion.expira());
    }

    @Transactional
    public void logout(String sesion, int usuario) { repository.revocar(sesion, usuario); }

    @Transactional(readOnly = true)
    public AuthDtos.Perfil perfil(int usuario) { return repository.perfil(usuario); }

    @Transactional(readOnly = true)
    public List<GestionUsuariosDtos.Asignacion> roles(int usuario) { return usuarios.accesosVigentes(usuario); }

    private static BadCredentialsException denegado() { return new BadCredentialsException("Credenciales no validas"); }
}
