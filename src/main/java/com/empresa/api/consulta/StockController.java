package com.empresa.api.consulta;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.consulta.StockDtos.*;
import com.empresa.api.common.Pagina;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}")
public class StockController {
    private final StockService service;
    public StockController(StockService service) { this.service = service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }

    @GetMapping("/articulos")
    public Pagina<Articulo> articulos(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                       @RequestParam MultiValueMap<String, String> query) {
        return service.articulos(usuario(jwt), empresa, query);
    }
    @GetMapping("/articulos/{referencia}")
    public Articulo articulo(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable String referencia) {
        return service.articulo(usuario(jwt), empresa, referencia);
    }
    @GetMapping("/almacenes")
    public Pagina<Almacen> almacenes(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                      @RequestParam MultiValueMap<String, String> query) {
        return service.almacenes(usuario(jwt), empresa, query);
    }
    @GetMapping("/almacenes/{id}")
    public Almacen almacen(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.almacen(usuario(jwt), empresa, id);
    }
    @GetMapping("/almacenes/{id}/stock")
    public Pagina<Existencia> stock(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                     @RequestParam MultiValueMap<String, String> query) {
        return service.stock(usuario(jwt), empresa, id, query);
    }
    @GetMapping("/almacenes/{id}/movimientos")
    public Pagina<Movimiento> movimientosAlmacen(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                                   @RequestParam MultiValueMap<String, String> query) {
        return service.movimientos(usuario(jwt), empresa, id, query);
    }
    @GetMapping("/movimientos-stock")
    public Pagina<Movimiento> movimientos(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                           @RequestParam MultiValueMap<String, String> query) {
        return service.movimientos(usuario(jwt), empresa, null, query);
    }
    @GetMapping("/movimientos-stock/{id}")
    public Movimiento movimiento(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.movimiento(usuario(jwt), empresa, id);
    }
}
