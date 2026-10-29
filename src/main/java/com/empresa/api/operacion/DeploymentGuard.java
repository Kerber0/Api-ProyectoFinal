package com.empresa.api.operacion;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Arrays;

@Component
@Profile("despliegue")
public class DeploymentGuard {
    public DeploymentGuard(Environment environment) {
        boolean incompatible = Arrays.stream(environment.getActiveProfiles()).anyMatch(p -> p.equals("dev") || p.equals("test"));
        int apiPort = environment.getProperty("server.port", Integer.class, 8443);
        int managementPort = environment.getProperty("management.server.port", Integer.class, -1);
        if (incompatible || !environment.getProperty("server.ssl.enabled", Boolean.class, false)
                || !environment.getProperty("dorna.limits.enabled", Boolean.class, false)
                || !"none".equalsIgnoreCase(environment.getProperty("server.forward-headers-strategy", ""))
                || !"127.0.0.1".equals(environment.getProperty("management.server.address"))
                || managementPort <= 0 || managementPort == apiPort) {
            throw new IllegalStateException("Despliegue requiere HTTPS, limites, cabeceras reenviadas deshabilitadas y gestion en loopback con puerto propio; no combinar con dev/test");
        }
    }
}
