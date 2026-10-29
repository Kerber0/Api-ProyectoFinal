package com.empresa.api.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record Login(@NotBlank @Size(max = 50) String usuario,
                        @NotBlank @Size(max = 200) String password) {
        @Override public String toString() { return "Login[credenciales omitidas]"; }
    }

    public record Refresh(@NotBlank @Size(max = 128) String refreshToken) {
        @Override public String toString() { return "Refresh[token omitido]"; }
    }

    public record Tokens(String accessToken, String refreshToken, String tokenType, long expiresIn) {
        @Override public String toString() { return "Tokens[omitidos]"; }
    }

    public record Perfil(int id, String usuario) {}
    public record Empresa(int id, String nombre) {}
}
