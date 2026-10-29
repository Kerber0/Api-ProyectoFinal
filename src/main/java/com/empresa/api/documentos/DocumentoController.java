package com.empresa.api.documentos;

import com.empresa.api.common.Pagina;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import static com.empresa.api.documentos.DocumentoDtos.*;

@RestController @RequestMapping("/api/v1/empresas/{empresa}")
public class DocumentoController {
    private final DocumentoService service;
    public DocumentoController(DocumentoService service) { this.service=service; }
    private int usuario(Jwt jwt) { return Integer.parseInt(jwt.getSubject()); }
    @GetMapping("/documentos/{tipo}/{referencia}")
    public Pagina<Detalle> listar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable String tipo,
            @PathVariable int referencia,@RequestParam MultiValueMap<String,String> query) {
        return service.listar(usuario(jwt),empresa,tipo,referencia,false,query);
    }
    @PostMapping(value="/documentos/{tipo}/{referencia}",consumes={"application/pdf","image/png","image/jpeg"})
    @ResponseStatus(HttpStatus.CREATED)
    public Detalle crear(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable String tipo,
            @PathVariable int referencia,@RequestHeader("Content-Type") String mime,@RequestParam MultiValueMap<String,String> query,@RequestBody byte[] contenido) {
        return service.crear(usuario(jwt),empresa,tipo,referencia,mime,query,contenido);
    }
    @GetMapping("/documentos/{tipo}/{referencia}/{id}")
    public Detalle consultar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable String tipo,@PathVariable int referencia,@PathVariable int id) {
        return service.consultar(usuario(jwt),empresa,tipo,referencia,id,false);
    }
    @GetMapping("/documentos/{tipo}/{referencia}/{id}/contenido")
    public ResponseEntity<byte[]> descargar(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable String tipo,@PathVariable int referencia,@PathVariable int id) {
        return respuesta(service.descargar(usuario(jwt),empresa,tipo,referencia,id,false));
    }
    @GetMapping("/mi-trabajo/ausencias/{ausencia}/justificantes")
    public Pagina<Detalle> propios(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int ausencia,@RequestParam MultiValueMap<String,String> query) {
        return service.listar(usuario(jwt),empresa,"ausencias",ausencia,true,query);
    }
    @GetMapping("/mi-trabajo/ausencias/{ausencia}/justificantes/{id}")
    public Detalle propio(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int ausencia,@PathVariable int id) {
        return service.consultar(usuario(jwt),empresa,"ausencias",ausencia,id,true);
    }
    @GetMapping("/mi-trabajo/ausencias/{ausencia}/justificantes/{id}/contenido")
    public ResponseEntity<byte[]> descargarPropio(@AuthenticationPrincipal Jwt jwt,@PathVariable int empresa,@PathVariable int ausencia,@PathVariable int id) {
        return respuesta(service.descargar(usuario(jwt),empresa,"ausencias",ausencia,id,true));
    }
    private ResponseEntity<byte[]> respuesta(Contenido contenido) {
        var d=contenido.documento();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.mime())).contentLength(d.tamano())
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.nombre(),StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store").header("X-Content-Type-Options","nosniff")
                .header("Content-Security-Policy","sandbox").body(contenido.bytes());
    }
}
