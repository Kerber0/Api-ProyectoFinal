package com.empresa.api.facturacion;

import com.empresa.api.common.Pagina;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.facturacion.FacturacionDtos.*;

@RestController @RequestMapping("/api/v1/empresas/{empresa}/facturacion/{tipo}")
public class FacturacionController {
    private final FacturacionService service;
    public FacturacionController(FacturacionService service) { this.service=service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    @GetMapping("/series")
    public Pagina<Serie> series(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@RequestParam MultiValueMap<String,String> query) { return service.series(usuario(jwt),empresa,tipo,query); }
    @PostMapping("/series") @ResponseStatus(HttpStatus.CREATED)
    public Serie crearSerie(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@RequestBody NuevaSerie request) { return service.crearSerie(usuario(jwt),empresa,tipo,request); }
    @GetMapping("/documentos")
    public Pagina<Resumen> listar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@RequestParam MultiValueMap<String,String> query) { return service.listar(usuario(jwt),empresa,tipo,query); }
    @PostMapping("/documentos") @ResponseStatus(HttpStatus.CREATED)
    public Detalle crear(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@RequestBody Nuevo request) { return service.crear(usuario(jwt),empresa,tipo,request); }
    @GetMapping("/documentos/{id}")
    public Detalle consultar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@PathVariable int id) { return service.consultar(usuario(jwt),empresa,tipo,id); }
    @PutMapping("/documentos/{id}/emision")
    public Detalle emitir(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@PathVariable int id) { return service.emitir(usuario(jwt),empresa,tipo,id); }
    @PutMapping("/documentos/{id}/anulacion")
    public Detalle anular(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@PathVariable int id,@RequestBody Anular request) { return service.anular(usuario(jwt),empresa,tipo,id,request); }
    @PostMapping("/documentos/{id}/rectificacion") @ResponseStatus(HttpStatus.CREATED)
    public Detalle rectificar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable Tipo tipo,@PathVariable int id,@RequestBody Rectificar request) { return service.rectificar(usuario(jwt),empresa,tipo,id,request); }
}
