package com.empresa.api.operacion;

import com.empresa.api.common.ApiError;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "dorna.limits.enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestLimitsFilter extends OncePerRequestFilter {
    private record Window(long expires, int requests, int writes) {}
    private final Map<String, Window> clients = new HashMap<>();
    private final Clock clock;
    private final ObjectMapper json;
    private final int requests, writes, maxClients, maxBody;
    private long nextCleanup;

    public RequestLimitsFilter(Clock clock, ObjectMapper json,
            @Value("${dorna.limits.requests-per-minute:300}") int requests,
            @Value("${dorna.limits.writes-per-minute:20}") int writes,
            @Value("${dorna.limits.max-clients:10000}") int maxClients,
            @Value("${dorna.limits.max-body-bytes:16384}") int maxBody) {
        if (requests < 1 || writes < 1 || maxClients < 1 || maxClients > 100000 || maxBody < 1 || maxBody > 1048576) {
            throw new IllegalArgumentException("Limites HTTP fuera de rango");
        }
        this.clock = clock; this.json = json; this.requests = requests; this.writes = writes;
        this.maxClients = maxClients; this.maxBody = maxBody;
    }

    // Una ventana por IP real de conexion; nunca confiar en X-Forwarded-For.
    private synchronized long retryAfter(String address, boolean write) {
        long now = clock.millis();
        if (now >= nextCleanup) {
            clients.values().removeIf(window -> window.expires <= now);
            nextCleanup = now + 1000;
        }
        Window old = clients.get(address);
        if (old == null || old.expires <= now) {
            if (old == null && clients.size() >= maxClients) { return 60; }
            old = new Window(now + 60000, 0, 0);
        }
        if (old.requests >= requests || (write && old.writes >= writes)) {
            return Math.max(1, (old.expires - now + 999) / 1000);
        }
        clients.put(address, new Window(old.expires, old.requests + 1, old.writes + (write ? 1 : 0)));
        return 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Todos los POST consumen el cupo estricto: no depende de variantes de la URL de login.
        long retry = retryAfter(request.getRemoteAddr(), !List.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()));
        if (retry > 0) {
            response.setHeader("Retry-After", Long.toString(retry));
            reject(request, response, 429, "LIMITE_SOLICITUDES", "Reintentar tras el intervalo indicado");
            return;
        }
        // Solo la ruta binaria recibe el limite documental; JSON y multipart conservan 16 KiB.
        int limite = "POST".equals(request.getMethod())
                && List.of("application/pdf","image/png","image/jpeg").contains(com.empresa.api.documentos.DocumentoValidacion.mime(request.getContentType()))
                && request.getRequestURI().matches(
                "/api/v1/empresas/[1-9][0-9]*/documentos/(obras|presupuestos|ausencias)/[1-9][0-9]*")
                ? com.empresa.api.documentos.DocumentoValidacion.MAX_BYTES : maxBody;
        if (request.getContentLengthLong() > limite) {
            reject(request, response, 413, "CUERPO_DEMASIADO_GRANDE", "Cuerpo de solicitud demasiado grande");
            return;
        }
        // Limita tambien cuerpos chunked o sin Content-Length antes de deserializar JSON.
        byte[] body = request.getInputStream().readNBytes(limite + 1);
        if (body.length > limite) {
            reject(request, response, 413, "CUERPO_DEMASIADO_GRANDE", "Cuerpo de solicitud demasiado grande");
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Solo lectura sincrona"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        }, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, int status, String code, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        json.writeValue(response.getOutputStream(), ApiError.of(request, status, code, detail, List.of()));
    }
}
