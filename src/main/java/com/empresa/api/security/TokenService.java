package com.empresa.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class TokenService {
    static final Duration ACCESS_TTL = Duration.ofMinutes(10);
    static final Duration SESSION_TTL = Duration.ofDays(7);
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder encoder, Clock clock,
                        @Value("${dorna.security.issuer}") String issuer,
                        @Value("${dorna.security.audience}") String audience) {
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = issuer;
        this.audience = audience;
    }

    public String nuevoRefresh(String sesion) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return sesion + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    public AuthDtos.Tokens emitir(int usuario, String sesion, int version, String refresh, Instant limite) {
        Instant ahora = clock.instant();
        Instant expira = ahora.plus(ACCESS_TTL).isBefore(limite) ? ahora.plus(ACCESS_TTL) : limite;
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .subject(Integer.toString(usuario)).issuedAt(ahora).notBefore(ahora).expiresAt(expira)
                .claim("sid", sesion).claim("ver", version).build();
        String access = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new AuthDtos.Tokens(access, refresh, "Bearer", Duration.between(ahora, expira).toSeconds());
    }
}
