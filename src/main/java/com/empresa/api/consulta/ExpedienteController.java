package com.empresa.api.consulta;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import static com.empresa.api.consulta.ExpedienteDtos.*;
import com.empresa.api.common.Pagina;

@RestController
@RequestMapping("/api/v1/empresas/{empresa}")
public class ExpedienteController {
    private final ExpedienteService service;
    public ExpedienteController(ExpedienteService service) { this.service = service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }

    @GetMapping("/presupuestos")
    public Pagina<PresupuestoResumen> presupuestos(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                                    @RequestParam MultiValueMap<String, String> query) {
        return service.presupuestos(usuario(jwt), empresa, query);
    }
    @GetMapping("/presupuestos/{id}")
    public PresupuestoDetalle presupuesto(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id) {
        return service.presupuesto(usuario(jwt), empresa, id);
    }
    @GetMapping("/presupuestos/{id}/lineas")
    public Pagina<Linea> lineas(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id,
                                @RequestParam MultiValueMap<String, String> query) {
        return service.lineas(usuario(jwt), empresa, id, query);
    }
    @GetMapping("/presupuestos/{id}/lineas/{linea}")
    public Linea linea(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int id, @PathVariable int linea) {
        return service.linea(usuario(jwt), empresa, id, linea);
    }
    @GetMapping("/obras/{obra}/documentos")
    public Pagina<Documento> documentos(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int obra,
                                          @RequestParam MultiValueMap<String, String> query) {
        return service.documentos(usuario(jwt), empresa, obra, query);
    }
    @GetMapping("/obras/{obra}/documentos/{id}")
    public Documento documento(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa, @PathVariable int obra, @PathVariable int id) {
        return service.documento(usuario(jwt), empresa, obra, id);
    }
    @GetMapping("/obras/{obra}/documentos/{id}/contenido")
    public ResponseEntity<byte[]> descargar(@AuthenticationPrincipal Jwt jwt, @PathVariable int empresa,
                                             @PathVariable int obra, @PathVariable int id) {
        byte[] bytes = service.descargar(usuario(jwt), empresa, obra, id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("documento-" + id).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff").body(bytes);
    }
}
