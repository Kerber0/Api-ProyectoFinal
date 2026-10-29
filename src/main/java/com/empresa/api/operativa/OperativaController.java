package com.empresa.api.operativa;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.operativa.OperativaDtos.*;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}/gestion")
public class OperativaController {
    private final OperativaService service;
    public OperativaController(OperativaService service) {this.service=service;}
    private int usuario(Jwt jwt){return Integer.parseInt(jwt.getSubject());}

    @PostMapping("/incidencias") @ResponseStatus(HttpStatus.CREATED)
    public IncidenciaFicha crearIncidencia(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@Valid @RequestBody IncidenciaEntrada data){
        return service.incidencia(usuario(jwt),empresa,null,data);
    }
    @PutMapping("/incidencias/{id}")
    public IncidenciaFicha actualizarIncidencia(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@Valid @RequestBody IncidenciaEntrada data){
        return service.incidencia(usuario(jwt),empresa,id,data);
    }
    @PostMapping("/tareas") @ResponseStatus(HttpStatus.CREATED)
    public TareaFicha crearTarea(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@Valid @RequestBody TareaEntrada data){
        return service.tarea(usuario(jwt),empresa,null,data);
    }
    @PutMapping("/tareas/{id}")
    public TareaFicha actualizarTarea(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@Valid @RequestBody TareaEntrada data){
        return service.tarea(usuario(jwt),empresa,id,data);
    }
    @PostMapping("/eventos") @ResponseStatus(HttpStatus.CREATED)
    public EventoFicha crearEvento(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@Valid @RequestBody EventoEntrada data){
        return service.evento(usuario(jwt),empresa,null,data);
    }
    @PutMapping("/eventos/{id}")
    public EventoFicha actualizarEvento(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@Valid @RequestBody EventoEntrada data){
        return service.evento(usuario(jwt),empresa,id,data);
    }
}
