package com.empresa.api.tesoreria;

import com.empresa.api.common.Pagina;
import com.empresa.api.facturacion.FacturacionDtos.Tipo;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.empresa.api.tesoreria.TesoreriaDtos.*;

@RestController @RequestMapping("/api/v1/empresas/{empresa}/tesoreria/{tipo}")
public class TesoreriaController {
    private final TesoreriaService service;
    public TesoreriaController(TesoreriaService service) { this.service=service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    @GetMapping("/documentos/{id}/vencimientos")
    public List<Vencimiento> plan(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@PathVariable int id) { return service.plan(usuario(jwt),empresa,tipo,id); }
    @PutMapping("/documentos/{id}/vencimientos")
    public List<Vencimiento> planificar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@PathVariable int id,@RequestBody Plan request) { return service.planificar(usuario(jwt),empresa,tipo,id,request); }
    @GetMapping("/vencimientos")
    public Pagina<Vencimiento> vencimientos(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@RequestParam MultiValueMap<String,String> query) { return service.vencimientos(usuario(jwt),empresa,tipo,query); }
    @GetMapping("/movimientos")
    public Pagina<Resumen> movimientos(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@RequestParam MultiValueMap<String,String> query) { return service.movimientos(usuario(jwt),empresa,tipo,query); }
    @PostMapping("/movimientos") @ResponseStatus(HttpStatus.CREATED)
    public Movimiento registrar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@RequestBody NuevoMovimiento request) { return service.registrar(usuario(jwt),empresa,tipo,request); }
    @GetMapping("/movimientos/{id}")
    public Movimiento consultar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@PathVariable int id) { return service.consultar(usuario(jwt),empresa,tipo,id); }
    @PutMapping("/movimientos/{id}/conciliacion")
    public Movimiento conciliar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@PathVariable int id,@RequestBody Conciliar request) { return service.conciliar(usuario(jwt),empresa,tipo,id,request); }
    @PutMapping("/movimientos/{id}/reversion")
    public Movimiento revertir(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@PathVariable int id,@RequestBody Revertir request) { return service.revertir(usuario(jwt),empresa,tipo,id,request); }
}
