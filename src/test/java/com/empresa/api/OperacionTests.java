package com.empresa.api;

import com.empresa.api.common.RequestIdFilter;
import com.empresa.api.operacion.DeploymentGuard;
import com.empresa.api.operacion.RequestLimitsFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class OperacionTests {
    private final ObjectMapper json = new ObjectMapper();
    private static class MutableClock extends Clock {
        long millis;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
    private MockHttpServletRequest request(String method, String ip, byte[] body) {
        var request = new MockHttpServletRequest(method, "/api/v1/auth/login");
        request.setRemoteAddr(ip);
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "id-servidor");
        request.setContent(body);
        return request;
    }
    private MockHttpServletResponse perform(RequestLimitsFilter filter, String method, String ip) throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(request(method, ip, new byte[0]), response, (req, res) -> res.getWriter().write("OK"));
        return response;
    }
    @Test
    void limitaEscriturasSinBloquearLecturasYExpira() throws Exception {
        var clock = new MutableClock();
        var filter = new RequestLimitsFilter(clock, json, 10, 2, 5, 64);
        assertThat(perform(filter, "POST", "1").getStatus()).isEqualTo(200);
        assertThat(perform(filter, "POST", "1").getStatus()).isEqualTo(200);
        var limited = perform(filter, "POST", "1");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
        assertThat(json.readTree(limited.getContentAsString()).get("requestId").asText()).isEqualTo("id-servidor");
        assertThat(limited.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(perform(filter, "GET", "1").getStatus()).isEqualTo(200);
        clock.millis = 59001;
        assertThat(perform(filter, "POST", "1").getHeader("Retry-After")).isEqualTo("1");
        clock.millis = 60000;
        assertThat(perform(filter, "POST", "1").getStatus()).isEqualTo(200);
    }
    @Test
    void ipRealYCupoGeneralNoSeEludenConCabecerasNiUrlCodificada() throws Exception {
        var filter = new RequestLimitsFilter(Clock.systemUTC(), json, 1, 1, 5, 64);
        perform(filter, "GET", "1");
        var request = request("GET", "1", new byte[0]);
        request.setRequestURI("/%61pi/v1/auth/login");
        request.addHeader("X-Forwarded-For", "2");
        request.addHeader("Forwarded", "for=3");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("No debe llegar al controlador"));
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(perform(filter, "GET", "2").getStatus()).isEqualTo(200);
    }
    @Test
    void mapaAcotadoNoExpulsaClientesActivosYRecuperaCapacidad() throws Exception {
        var clock = new MutableClock();
        var filter = new RequestLimitsFilter(clock, json, 1, 1, 2, 64);
        perform(filter, "GET", "1"); perform(filter, "GET", "2");
        assertThat(perform(filter, "GET", "3").getStatus()).isEqualTo(429);
        assertThat(perform(filter, "GET", "1").getStatus()).isEqualTo(429);
        clock.millis = 60000;
        assertThat(perform(filter, "GET", "3").getStatus()).isEqualTo(200);
    }
    @Test
    void solicitudesConcurrentesRespetanElCupo() throws Exception {
        var filter = new RequestLimitsFilter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), json, 7, 7, 2, 64);
        var aceptadas = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 50; i++) {
                tasks.add(() -> { if (perform(filter, "POST", "1").getStatus() == 200) { aceptadas.incrementAndGet(); } return null; });
            }
            for (var result : executor.invokeAll(tasks)) { result.get(); }
        }
        assertThat(aceptadas.get()).isEqualTo(7);
    }
    @Test
    void jsonSeReproduceIntactoDentroDelLimite() throws Exception {
        byte[] body = "{\"usuario\":\"Nicolás\"}".getBytes(StandardCharsets.UTF_8);
        var filter = new RequestLimitsFilter(Clock.systemUTC(), json, 5, 5, 5, body.length);
        filter.doFilter(request("POST", "1", body), new MockHttpServletResponse(), (req, res) -> {
            assertThat(req.getInputStream().readAllBytes()).containsExactly(body);
            assertThat(((HttpServletRequest) req).getReader().readLine()).isEqualTo(new String(body, StandardCharsets.UTF_8));
        });
    }
    @Test
    void rechazaContentLengthGrandeAntesDeLeer() throws Exception {
        var filter = new RequestLimitsFilter(Clock.systemUTC(), json, 5, 5, 5, 4);
        var response = new MockHttpServletResponse();
        filter.doFilter(request("POST", "1", new byte[5]), response, (req, res) -> fail("No debe procesar el cuerpo"));
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(json.readTree(response.getContentAsString()).get("codigo").asText()).isEqualTo("CUERPO_DEMASIADO_GRANDE");
    }
    @Test
    void rechazaCuerpoSinContentLength() throws Exception {
        var filter = new RequestLimitsFilter(Clock.systemUTC(), json, 5, 5, 5, 4);
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setContent(new byte[5]);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("No debe procesar el cuerpo"));
        assertThat(response.getStatus()).isEqualTo(413);
    }
    @Test
    void configuracionDeLimitesInvalidaImpideArranque() {
        assertThatThrownBy(() -> new RequestLimitsFilter(Clock.systemUTC(), json, 0, 1, 2, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestLimitsFilter(Clock.systemUTC(), json, 1, 1, 100001, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RequestLimitsFilter(Clock.systemUTC(), json, 1, 1, 2, 1048577)).isInstanceOf(IllegalArgumentException.class);
    }
    private MockEnvironment environment() {
        return new MockEnvironment().withProperty("server.ssl.enabled", "true")
                .withProperty("server.port", "8443").withProperty("dorna.limits.enabled", "true")
                .withProperty("server.forward-headers-strategy", "none")
                .withProperty("management.server.address", "127.0.0.1").withProperty("management.server.port", "9091");
    }
    @Test
    void perfilDespliegueSeguroValido() { assertThatCode(() -> new DeploymentGuard(environment())).doesNotThrowAnyException(); }
    @ParameterizedTest
    @CsvSource({"server.ssl.enabled,false", "dorna.limits.enabled,false", "management.server.address,0.0.0.0",
            "management.server.port,8443", "management.server.port,-1", "server.forward-headers-strategy,framework"})
    void perfilRechazaConfiguracionInsegura(String key, String value) {
        assertThatThrownBy(() -> new DeploymentGuard(environment().withProperty(key, value))).isInstanceOf(IllegalStateException.class);
    }
    @Test
    void perfilDespliegueNoActivaInicializadorDemo() {
        for (String profile : new String[]{"dev", "test"}) {
            var environment = environment(); environment.setActiveProfiles("despliegue", profile);
            assertThatThrownBy(() -> new DeploymentGuard(environment)).isInstanceOf(IllegalStateException.class);
        }
    }
}
