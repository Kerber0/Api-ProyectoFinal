package com.empresa.api.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecretKey jwtKey(@Value("${dorna.security.secret}") String secret) {
        if (!secret.matches("[a-fA-F0-9]{64}")) {
            throw new IllegalStateException("DORNA_JWT_SECRET debe contener 32 bytes aleatorios en hexadecimal");
        }
        return new SecretKeySpec(HexFormat.of().parseHex(secret), "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, Clock clock,
                          @Value("${dorna.security.issuer}") String issuer,
                          @Value("${dorna.security.audience}") String audience) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> required = jwt -> {
            boolean valid = jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
                    && !jwt.getIssuedAt().isAfter(clock.instant()) && jwt.getAudience() != null
                    && jwt.getAudience().contains(audience);
            return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(issuer), required));
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccesoRepository repository, Clock clock, SecurityErrors errors,
            @Value("${management.server.port:-1}") int managementPort) throws Exception {
        return http
                // Credenciales solo en Authorization/cuerpo JSON; no autenticacion mediante cookies.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(request -> managementPort > 0 && request.getLocalPort() == managementPort
                                && "127.0.0.1".equals(request.getRemoteAddr()) && "GET".equals(request.getMethod())
                                && (request.getRequestURI().equals("/actuator/health")
                                    || request.getRequestURI().startsWith("/actuator/health/")
                                    || request.getRequestURI().equals("/actuator/metrics")
                                    || request.getRequestURI().startsWith("/actuator/metrics/"))).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET,"/api/v1/auditoria","/api/v1/auditoria/{id}").authenticated()
                        .requestMatchers(HttpMethod.POST,"/api/v1/empresas/{empresa}/gestion/obras/{id}/contactos").authenticated()
                        .requestMatchers(HttpMethod.GET,"/api/v1/empresas/{empresa}/gestion/obras/{id}/contactos/{relacion}").authenticated()
                        .requestMatchers(HttpMethod.PUT,"/api/v1/empresas/{empresa}/gestion/obras/{id}/contactos/{relacion}").authenticated()
                        .requestMatchers(HttpMethod.DELETE,"/api/v1/empresas/{empresa}/gestion/obras/{id}/contactos/{relacion}").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/gestion/clientes/{id}",
                                "/api/v1/empresas/{empresa}/gestion/clientes/{id}/iban",
                                "/api/v1/empresas/{empresa}/gestion/proveedores/{id}",
                                "/api/v1/empresas/{empresa}/gestion/proveedores/{id}/iban",
                                "/api/v1/empresas/{empresa}/gestion/operarios/{id}",
                                "/api/v1/empresas/{empresa}/gestion/operarios/{id}/iban",
                                "/api/v1/empresas/{empresa}/gestion/obras/{id}",
                                "/api/v1/empresas/{empresa}/gestion/presupuestos/{id}",
                                "/api/v1/empresas/{empresa}/gestion/series/{tipo}").authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/empresas/{empresa}/gestion/clientes",
                                "/api/v1/empresas/{empresa}/gestion/proveedores",
                                "/api/v1/empresas/{empresa}/gestion/operarios",
                                "/api/v1/empresas/{empresa}/gestion/obras",
                                "/api/v1/empresas/{empresa}/gestion/presupuestos",
                                "/api/v1/empresas/{empresa}/gestion/series/{tipo}").authenticated()
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/empresas/{empresa}/gestion/clientes/{id}",
                                "/api/v1/empresas/{empresa}/gestion/clientes/{id}/iban",
                                "/api/v1/empresas/{empresa}/gestion/clientes/{id}/actividad",
                                "/api/v1/empresas/{empresa}/gestion/proveedores/{id}",
                                "/api/v1/empresas/{empresa}/gestion/proveedores/{id}/iban",
                                "/api/v1/empresas/{empresa}/gestion/proveedores/{id}/actividad",
                                "/api/v1/empresas/{empresa}/gestion/operarios/{id}",
                                "/api/v1/empresas/{empresa}/gestion/operarios/{id}/iban",
                                "/api/v1/empresas/{empresa}/gestion/operarios/{id}/actividad",
                                "/api/v1/empresas/{empresa}/gestion/obras/{id}",
                                "/api/v1/empresas/{empresa}/gestion/obras/{id}/estado",
                                "/api/v1/empresas/{empresa}/gestion/presupuestos/{id}",
                                "/api/v1/empresas/{empresa}/gestion/presupuestos/{id}/estado").authenticated()
                        .requestMatchers(HttpMethod.POST,"/api/v1/empresas/{empresa}/documentos/{tipo}/{referencia}").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/documentos/{tipo}/{referencia}",
                                "/api/v1/empresas/{empresa}/documentos/{tipo}/{referencia}/{id}",
                                "/api/v1/empresas/{empresa}/documentos/{tipo}/{referencia}/{id}/contenido",
                                "/api/v1/empresas/{empresa}/mi-trabajo/ausencias/{ausencia}/justificantes",
                                "/api/v1/empresas/{empresa}/mi-trabajo/ausencias/{ausencia}/justificantes/{id}",
                                "/api/v1/empresas/{empresa}/mi-trabajo/ausencias/{ausencia}/justificantes/{id}/contenido").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/facturacion/{tipo}/series",
                                "/api/v1/empresas/{empresa}/facturacion/{tipo}/documentos",
                                "/api/v1/empresas/{empresa}/facturacion/{tipo}/documentos/{id}").authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/empresas/{empresa}/facturacion/{tipo}/series",
                                "/api/v1/empresas/{empresa}/facturacion/{tipo}/documentos",
                                "/api/v1/empresas/{empresa}/facturacion/{tipo}/documentos/{id}/rectificacion").authenticated()
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/empresas/{empresa}/facturacion/{tipo}/documentos/{id}/emision",
                                "/api/v1/empresas/{empresa}/facturacion/{tipo}/documentos/{id}/anulacion").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/obras/{obra}/observaciones",
                                "/api/v1/empresas/{empresa}/obras/{obra}/observaciones/{id}",
                                "/api/v1/empresas/{empresa}/mi-trabajo/obras/{obra}/observaciones",
                                "/api/v1/empresas/{empresa}/mi-trabajo/obras/{obra}/observaciones/{id}").authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/empresas/{empresa}/obras/{obra}/observaciones",
                                "/api/v1/empresas/{empresa}/mi-trabajo/obras/{obra}/observaciones").authenticated()
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/empresas/{empresa}/obras/{obra}/observaciones/{id}/resolucion").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/operarios/{id}/usuario",
                                "/api/v1/empresas/{empresa}/operarios/{id}/asignaciones",
                                "/api/v1/empresas/{empresa}/operarios/{id}/ausencias",
                                "/api/v1/empresas/{empresa}/mi-trabajo/empleado",
                                "/api/v1/empresas/{empresa}/mi-trabajo/obras",
                                "/api/v1/empresas/{empresa}/mi-trabajo/obras/{obra}",
                                "/api/v1/empresas/{empresa}/mi-trabajo/obras/{obra}/contactos",
                                "/api/v1/empresas/{empresa}/mi-trabajo/ausencias").authenticated()
                        .requestMatchers(HttpMethod.PUT,"/api/v1/empresas/{empresa}/operarios/{id}/usuario").authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/empresas/{empresa}/operarios/{id}/asignaciones",
                                "/api/v1/empresas/{empresa}/operarios/{id}/ausencias").authenticated()
                        .requestMatchers(HttpMethod.DELETE,
                                "/api/v1/empresas/{empresa}/operarios/{id}/usuario",
                                "/api/v1/empresas/{empresa}/operarios/{id}/asignaciones/{asignacion}",
                                "/api/v1/empresas/{empresa}/operarios/{id}/ausencias/{ausencia}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/usuarios", "/api/v1/usuarios/{id}",
                                "/api/v1/usuarios/{id}/accesos", "/api/v1/roles", "/api/v1/permisos",
                                "/api/v1/empresas/{empresa}/permisos").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/usuarios").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/usuarios/{id}", "/api/v1/usuarios/{id}/password",
                                "/api/v1/usuarios/{id}/roles-globales/{rol}",
                                "/api/v1/usuarios/{id}/empresas/{empresa}/roles/{rol}").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/usuarios/{id}/roles-globales/{rol}",
                                "/api/v1/usuarios/{id}/empresas/{empresa}/roles/{rol}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/empresas/{empresa}/ficha").authenticated()
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/empresas/{empresa}/clientes/{id}/visibilidad/{destino}",
                                "/api/v1/empresas/{empresa}/proveedores/{id}/visibilidad/{destino}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me", "/api/v1/auth/me/roles",
                                "/api/v1/empresas", "/api/v1/empresas/{id}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/openapi.json",
                                "/api/v1/empresas/{empresa}/clientes", "/api/v1/empresas/{empresa}/clientes/{id}",
                                "/api/v1/empresas/{empresa}/clientes/{id}/contactos",
                                "/api/v1/empresas/{empresa}/clientes/{id}/direcciones",
                                "/api/v1/empresas/{empresa}/obras", "/api/v1/empresas/{empresa}/obras/{id}",
                                "/api/v1/empresas/{empresa}/obras/{id}/contactos",
                                "/api/v1/empresas/{empresa}/obras/{obra}/contactos/{relacion}").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/informes/grupo",
                                "/api/v1/empresas/{empresa}/presupuestos", "/api/v1/empresas/{empresa}/presupuestos/{id}",
                                "/api/v1/empresas/{empresa}/presupuestos/{id}/lineas",
                                "/api/v1/empresas/{empresa}/presupuestos/{id}/lineas/{linea}",
                                "/api/v1/empresas/{empresa}/obras/{obra}/documentos",
                                "/api/v1/empresas/{empresa}/obras/{obra}/documentos/{id}",
                                "/api/v1/empresas/{empresa}/obras/{obra}/documentos/{id}/contenido").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/operarios", "/api/v1/empresas/{empresa}/operarios/{id}",
                                "/api/v1/empresas/{empresa}/operarios/{id}/partes",
                                "/api/v1/empresas/{empresa}/partes-mano-obra", "/api/v1/empresas/{empresa}/partes-mano-obra/{id}",
                                "/api/v1/empresas/{empresa}/obras/{id}/partes").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/facturas-cliente",
                                "/api/v1/empresas/{empresa}/facturas-cliente/{id}",
                                "/api/v1/empresas/{empresa}/facturas-cliente/{id}/lineas",
                                "/api/v1/empresas/{empresa}/facturas-cliente/{id}/lineas/{linea}",
                                "/api/v1/empresas/{empresa}/facturas-cliente/{id}/cobros",
                                "/api/v1/empresas/{empresa}/facturas-cliente/{id}/saldo",
                                "/api/v1/empresas/{empresa}/cobros",
                                "/api/v1/empresas/{empresa}/cobros/{id}",
                                "/api/v1/empresas/{empresa}/vencimientos-cliente",
                                "/api/v1/empresas/{empresa}/facturas-proveedor",
                                "/api/v1/empresas/{empresa}/facturas-proveedor/{id}",
                                "/api/v1/empresas/{empresa}/facturas-proveedor/{id}/lineas",
                                "/api/v1/empresas/{empresa}/facturas-proveedor/{id}/lineas/{linea}",
                                "/api/v1/empresas/{empresa}/facturas-proveedor/{id}/pagos",
                                "/api/v1/empresas/{empresa}/facturas-proveedor/{id}/saldo",
                                "/api/v1/empresas/{empresa}/pagos",
                                "/api/v1/empresas/{empresa}/pagos/{id}",
                                "/api/v1/empresas/{empresa}/vencimientos-proveedor").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/gestion/{tipo}/{id}/contactos",
                                "/api/v1/empresas/{empresa}/gestion/{tipo}/{id}/contactos/{contacto}",
                                "/api/v1/empresas/{empresa}/gestion/{tipo}/{id}/direcciones",
                                "/api/v1/empresas/{empresa}/gestion/{tipo}/{id}/direcciones/{direccion}").authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/empresas/{empresa}/gestion/{tipo}/{id}/contactos",
                                "/api/v1/empresas/{empresa}/gestion/{tipo}/{id}/direcciones").authenticated()
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/empresas/{empresa}/gestion/{tipo}/{id}/contactos/{contacto}",
                                "/api/v1/empresas/{empresa}/gestion/{tipo}/{id}/direcciones/{direccion}").authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/empresas/{empresa}/gestion/incidencias",
                                "/api/v1/empresas/{empresa}/gestion/tareas",
                                "/api/v1/empresas/{empresa}/gestion/eventos").authenticated()
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/empresas/{empresa}/gestion/incidencias/{id}",
                                "/api/v1/empresas/{empresa}/gestion/tareas/{id}",
                                "/api/v1/empresas/{empresa}/gestion/eventos/{id}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/empresas/{empresa}/tesoreria/prevision").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/empresas/{empresa}/dashboard").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/informes/facturacion-cliente",
                                "/api/v1/empresas/{empresa}/informes/facturacion-proveedor",
                                "/api/v1/empresas/{empresa}/informes/cobros",
                                "/api/v1/empresas/{empresa}/informes/pagos",
                                "/api/v1/empresas/{empresa}/informes/saldos-cliente",
                                "/api/v1/empresas/{empresa}/informes/saldos-proveedor",
                                "/api/v1/empresas/{empresa}/informes/obras-por-estado",
                                "/api/v1/empresas/{empresa}/informes/presupuestos-por-estado").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/articulos", "/api/v1/empresas/{empresa}/articulos/{referencia}",
                                "/api/v1/empresas/{empresa}/almacenes", "/api/v1/empresas/{empresa}/almacenes/{id}",
                                "/api/v1/empresas/{empresa}/almacenes/{id}/stock",
                                "/api/v1/empresas/{empresa}/almacenes/{id}/movimientos",
                                "/api/v1/empresas/{empresa}/movimientos-stock",
                                "/api/v1/empresas/{empresa}/movimientos-stock/{id}").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/proveedores", "/api/v1/empresas/{empresa}/proveedores/{id}",
                                "/api/v1/empresas/{empresa}/proveedores/{id}/contactos",
                                "/api/v1/empresas/{empresa}/proveedores/{id}/direcciones",
                                "/api/v1/empresas/{empresa}/proveedores/{id}/pedidos",
                                "/api/v1/empresas/{empresa}/pedidos-proveedor", "/api/v1/empresas/{empresa}/pedidos-proveedor/{id}",
                                "/api/v1/empresas/{empresa}/pedidos-proveedor/{id}/lineas",
                                "/api/v1/empresas/{empresa}/pedidos-proveedor/{pedido}/lineas/{id}").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/albaranes-cliente", "/api/v1/empresas/{empresa}/albaranes-cliente/{id}",
                                "/api/v1/empresas/{empresa}/albaranes-cliente/{id}/lineas",
                                "/api/v1/empresas/{empresa}/albaranes-cliente/{albaran}/lineas/{id}",
                                "/api/v1/empresas/{empresa}/facturas-cliente/{id}/albaranes",
                                "/api/v1/empresas/{empresa}/albaranes-proveedor", "/api/v1/empresas/{empresa}/albaranes-proveedor/{id}",
                                "/api/v1/empresas/{empresa}/albaranes-proveedor/{id}/lineas",
                                "/api/v1/empresas/{empresa}/albaranes-proveedor/{albaran}/lineas/{id}",
                                "/api/v1/empresas/{empresa}/facturas-proveedor/{id}/albaranes").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/incidencias", "/api/v1/empresas/{empresa}/incidencias/{id}",
                                "/api/v1/empresas/{empresa}/obras/{id}/incidencias",
                                "/api/v1/empresas/{empresa}/operarios/{id}/incidencias").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/eventos", "/api/v1/empresas/{empresa}/eventos/{id}",
                                "/api/v1/empresas/{empresa}/obras/{id}/eventos",
                                "/api/v1/empresas/{empresa}/tareas", "/api/v1/empresas/{empresa}/tareas/{id}",
                                "/api/v1/empresas/{empresa}/obras/{id}/tareas",
                                "/api/v1/empresas/{empresa}/operarios/{id}/tareas").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/empresas/{empresa}/tesoreria/{tipo}/documentos/{id}/vencimientos",
                                "/api/v1/empresas/{empresa}/tesoreria/{tipo}/vencimientos",
                                "/api/v1/empresas/{empresa}/tesoreria/{tipo}/movimientos",
                                "/api/v1/empresas/{empresa}/tesoreria/{tipo}/movimientos/{id}").authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/empresas/{empresa}/tesoreria/{tipo}/movimientos").authenticated()
                        .requestMatchers(HttpMethod.PUT,
                                "/api/v1/empresas/{empresa}/tesoreria/{tipo}/documentos/{id}/vencimientos",
                                "/api/v1/empresas/{empresa}/tesoreria/{tipo}/movimientos/{id}/conciliacion",
                                "/api/v1/empresas/{empresa}/tesoreria/{tipo}/movimientos/{id}/reversion").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, 401))
                        .accessDeniedHandler((req, res, ex) -> errors.write(req, res, 403)))
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint((req, res, ex) -> errors.write(req, res, 401))
                        .accessDeniedHandler((req, res, ex) -> errors.write(req, res, 403))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                            try {
                                int usuario = Integer.parseInt(token.getSubject());
                                String sesion = token.getClaimAsString("sid");
                                Object rawVersion = token.getClaims().get("ver");
                                if (!(rawVersion instanceof Number number) || sesion == null
                                        || !sesion.matches("[a-f0-9-]{36}")
                                        || number.longValue() != number.intValue()
                                        || !repository.sesionVigente(sesion, usuario, number.intValue(), clock.instant())) {
                                    throw new IllegalArgumentException();
                                }
                                return new JwtAuthenticationToken(token, List.of(), token.getSubject());
                            } catch (IllegalArgumentException ex) {
                                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"));
                            }
                        }))).build();
    }
}
