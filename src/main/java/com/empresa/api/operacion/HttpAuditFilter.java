package com.empresa.api.operacion;

import com.empresa.api.common.RequestIdFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@ConditionalOnProperty(name = "dorna.audit.enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpAuditFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(HttpAuditFilter.class);
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        boolean failed = true;
        try { chain.doFilter(request, response); failed = false; }
        finally {
            // No URL, parametros, cuerpos, cookies, credenciales ni direcciones personales.
            log.info("http requestId={} status={} durationMs={}", request.getAttribute(RequestIdFilter.ATTRIBUTE),
                    failed ? 500 : response.getStatus(), (System.nanoTime() - start) / 1000000);
        }
    }
}
