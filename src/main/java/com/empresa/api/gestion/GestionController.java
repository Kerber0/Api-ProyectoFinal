package com.empresa.api.gestion;

import com.empresa.api.common.Pagina;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.gestion.GestionDtos.*;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}/gestion")
public class GestionController {
    private final GestionMaestrosService maestros;
    private final GestionObrasService obras;
    public GestionController(GestionMaestrosService maestros,GestionObrasService obras) { this.maestros=maestros;this.obras=obras; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    @GetMapping("/clientes/{id}")
    public ClienteFicha cliente(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id) { return maestros.cliente(usuario(jwt),empresa,id); }
    @PostMapping("/clientes") @ResponseStatus(HttpStatus.CREATED)
    public ClienteFicha crearCliente(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@RequestBody ClienteEntrada dato) { return maestros.crearCliente(usuario(jwt),empresa,dato); }
    @PutMapping("/clientes/{id}")
    public ClienteFicha actualizarCliente(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody ClienteEntrada dato) { return maestros.actualizarCliente(usuario(jwt),empresa,id,dato); }
    @PutMapping("/clientes/{id}/actividad")
    public ClienteFicha actividadCliente(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody Actividad dato) { return maestros.actividadCliente(usuario(jwt),empresa,id,dato); }
    @GetMapping("/proveedores/{id}")
    public ProveedorFicha proveedor(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id) { return maestros.proveedor(usuario(jwt),empresa,id); }
    @PostMapping("/proveedores") @ResponseStatus(HttpStatus.CREATED)
    public ProveedorFicha crearProveedor(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@RequestBody ProveedorEntrada dato) { return maestros.crearProveedor(usuario(jwt),empresa,dato); }
    @PutMapping("/proveedores/{id}")
    public ProveedorFicha actualizarProveedor(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody ProveedorEntrada dato) { return maestros.actualizarProveedor(usuario(jwt),empresa,id,dato); }
    @PutMapping("/proveedores/{id}/actividad")
    public ProveedorFicha actividadProveedor(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody Actividad dato) { return maestros.actividadProveedor(usuario(jwt),empresa,id,dato); }
    @GetMapping("/clientes/{id}/iban") public IbanFicha ibanCliente(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id){return maestros.ibanCliente(usuario(jwt),empresa,id);}
    @PutMapping("/clientes/{id}/iban") public IbanFicha ibanCliente(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody IbanEntrada dato){return maestros.ibanCliente(usuario(jwt),empresa,id,dato);}
    @GetMapping("/proveedores/{id}/iban") public IbanFicha ibanProveedor(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id){return maestros.ibanProveedor(usuario(jwt),empresa,id);}
    @PutMapping("/proveedores/{id}/iban") public IbanFicha ibanProveedor(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody IbanEntrada dato){return maestros.ibanProveedor(usuario(jwt),empresa,id,dato);}
    @GetMapping("/operarios/{id}/iban") public IbanFicha ibanOperario(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id){return maestros.ibanOperario(usuario(jwt),empresa,id);}
    @PutMapping("/operarios/{id}/iban") public IbanFicha ibanOperario(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody IbanEntrada dato){return maestros.ibanOperario(usuario(jwt),empresa,id,dato);}
    @GetMapping("/operarios/{id}")
    public OperarioFicha operario(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id) { return maestros.operario(usuario(jwt),empresa,id); }
    @PostMapping("/operarios") @ResponseStatus(HttpStatus.CREATED)
    public OperarioFicha crearOperario(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@RequestBody OperarioEntrada dato) { return maestros.crearOperario(usuario(jwt),empresa,dato); }
    @PutMapping("/operarios/{id}")
    public OperarioFicha actualizarOperario(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody OperarioEntrada dato) { return maestros.actualizarOperario(usuario(jwt),empresa,id,dato); }
    @PutMapping("/operarios/{id}/actividad")
    public OperarioFicha actividadOperario(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody Actividad dato) { return maestros.actividadOperario(usuario(jwt),empresa,id,dato); }
    @GetMapping("/obras/{id}")
    public ObraFicha obra(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id) { return obras.obra(usuario(jwt),empresa,id); }
    @PostMapping("/obras") @ResponseStatus(HttpStatus.CREATED)
    public ObraFicha crearObra(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@RequestBody ObraEntrada dato) { return obras.crearObra(usuario(jwt),empresa,dato); }
    @PutMapping("/obras/{id}")
    public ObraFicha actualizarObra(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody ObraEntrada dato) { return obras.actualizarObra(usuario(jwt),empresa,id,dato); }
    @PutMapping("/obras/{id}/estado")
    public ObraFicha estadoObra(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody CambioObra dato) { return obras.estadoObra(usuario(jwt),empresa,id,dato); }
    @GetMapping("/presupuestos/{id}")
    public PresupuestoFicha presupuesto(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id) { return obras.presupuesto(usuario(jwt),empresa,id); }
    @PostMapping("/presupuestos") @ResponseStatus(HttpStatus.CREATED)
    public PresupuestoFicha crearPresupuesto(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@RequestBody PresupuestoEntrada dato) { return obras.crearPresupuesto(usuario(jwt),empresa,dato); }
    @PutMapping("/presupuestos/{id}")
    public PresupuestoFicha actualizarPresupuesto(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody PresupuestoEntrada dato) { return obras.actualizarPresupuesto(usuario(jwt),empresa,id,dato); }
    @PutMapping("/presupuestos/{id}/estado")
    public PresupuestoFicha estadoPresupuesto(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int id,@RequestBody CambioPresupuesto dato) { return obras.estadoPresupuesto(usuario(jwt),empresa,id,dato); }
    @GetMapping("/series/{tipo}")
    public Pagina<SerieFicha> series(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable TipoSerie tipo,@RequestParam MultiValueMap<String,String> q) { return obras.series(usuario(jwt),empresa,tipo,q); }
    @PostMapping("/series/{tipo}") @ResponseStatus(HttpStatus.CREATED)
    public SerieFicha crearSerie(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable TipoSerie tipo,@RequestBody SerieEntrada dato) { return obras.crearSerie(usuario(jwt),empresa,tipo,dato); }
}
