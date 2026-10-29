package com.empresa.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;

@Component
@Profile("provisionar")
public class UsuariosInicialesRunner implements ApplicationRunner {
    private final UsuariosInicialesService service;
    private final ConfigurableApplicationContext context;
    private final String payload;
    public UsuariosInicialesRunner(UsuariosInicialesService service, ConfigurableApplicationContext context,
            @Value("${dorna.usuarios.iniciales}") String payload) {
        this.service = service; this.context = context; this.payload = payload;
    }
    @Override
    public void run(ApplicationArguments args) {
        var claves = new HashMap<String, String>();
        try {
            var json = new ObjectMapper().readTree(payload);
            json.properties().forEach(p -> claves.put(p.getKey(), p.getValue().asText()));
        } catch (RuntimeException ex) {
            // No incluir el documento ni la excepcion de Jackson: contiene secretos.
            throw new IllegalArgumentException("Formato de credenciales locales invalido");
        }
        try { service.provisionar(claves); }
        finally { claves.clear(); }
        System.out.println("Usuarios iniciales preparados: " + String.join(", ", UsuariosInicialesService.USUARIOS));
        SpringApplication.exit(context, () -> 0);
    }
}
