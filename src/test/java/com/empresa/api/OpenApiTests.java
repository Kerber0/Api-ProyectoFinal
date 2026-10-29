package com.empresa.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OpenApiTests extends PostgreSqlIntegrationTest {
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    private JsonNode spec;

    @BeforeEach
    void cargarContrato() throws Exception {
        try (var stream = getClass().getResourceAsStream("/static/api/v1/openapi.json")) {
            assertThat(stream).isNotNull();
            spec = new ObjectMapper().readTree(stream);
        }
    }

    @Test
    void contratoCubreLasOperacionesPublicadasPorLosControladores() {
        Set<String> implementadas = new HashSet<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("com.empresa.api")) { return; }
            assertThat(mapping.getMethodsCondition().getMethods()).as(handler.toString()).isNotEmpty();
            for (String path : mapping.getPatternValues()) {
                assertThat(path).startsWith("/api/v1/");
                for (var verb : mapping.getMethodsCondition().getMethods()) {
                    implementadas.add(verb.name().toLowerCase(java.util.Locale.ROOT) + " " + path.substring("/api/v1".length()));
                }
            }
        });
        assertThat(implementadas).isNotEmpty();
        Set<String> documentadas = new HashSet<>();
        spec.get("paths").properties().forEach(path -> path.getValue().properties().stream()
                .filter(operation -> Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace").contains(operation.getKey()))
                .forEach(operation -> documentadas.add(operation.getKey() + " " + path.getKey())));
        assertThat(documentadas).containsExactlyInAnyOrderElementsOf(implementadas);
    }

    @Test
    void referenciasInternasYParametrosDeRutaSonValidos() {
        verificarReferencias(spec);
        var placeholder = Pattern.compile("\\{(\\w+)\\}");
        spec.get("paths").properties().forEach(path -> {
            Set<String> esperados = new HashSet<>();
            placeholder.matcher(path.getKey()).results().forEach(match -> esperados.add(match.group(1)));
            path.getValue().properties().forEach(operation -> {
                Set<String> declarados = new HashSet<>();
                for (var parameter : operation.getValue().path("parameters")) {
                    if (parameter.path("in").asText().equals("path")) {
                        assertThat(parameter.path("required").asBoolean()).isTrue();
                        assertThat(declarados.add(parameter.get("name").asText())).isTrue();
                    }
                }
                assertThat(declarados).as(path.getKey()).containsExactlyInAnyOrderElementsOf(esperados);
            });
        });
    }

    private void verificarReferencias(JsonNode node) {
        if (node.has("$ref")) {
            String ref = node.get("$ref").asText();
            assertThat(ref).startsWith("#/");
            assertThat(spec.at(ref.substring(1)).isMissingNode()).as(ref).isFalse();
        }
        if (node.isObject() || node.isArray()) { node.forEach(this::verificarReferencias); }
    }

    @Test
    void albaranesDocumentanFacturaOpcionalYFiltrosActuales() {
        var schema = spec.at("/components/schemas/Albaran");
        assertThat(schema.get("properties").has("facturaId")).isTrue();
        assertThat(schema.get("properties").has("obraId")).isFalse();
        assertThat(schema.get("properties").has("pedidoId")).isFalse();
        assertThat(schema.at("/properties/facturaId/type").toString()).contains("integer", "null");
        for (String tipo : new String[]{"cliente", "proveedor"}) {
            var params = spec.get("paths").get("/empresas/{empresa}/albaranes-" + tipo).get("get").get("parameters");
            Set<String> filtros = new HashSet<>();
            params.forEach(p -> filtros.add(p.get("name").asText()));
            assertThat(filtros).contains("factura").doesNotContain("obra", "pedido");
        }
    }

    @Test
    void limitesHttpCompartenElContratoDeErrorYDeclaranReintento() {
        spec.get("paths").properties().forEach(path -> path.getValue().properties().forEach(operation -> {
            var responses = operation.getValue().get("responses");
            for (String code : new String[]{"413", "429"}) {
                assertThat(responses.get(code).get("content").get("application/json").get("schema").get("$ref").asText())
                        .isEqualTo("#/components/schemas/Error");
            }
            assertThat(responses.get("429").get("headers").get("Retry-After").get("schema").get("minimum").asInt()).isEqualTo(1);
        }));
    }

    @Test
    void informesExigenFechasYDocumentanSeriesAcotadasConDecimalesExactos() {
        for (String ruta : new String[]{"facturacion-cliente", "facturacion-proveedor", "cobros", "pagos", "saldos-cliente", "saldos-proveedor"}) {
            var operation = spec.get("paths").get("/empresas/{empresa}/informes/" + ruta).get("get");
            assertThat(operation.get("parameters")).hasSize(3);
            for (var parameter : operation.get("parameters")) {
                assertThat(parameter.get("required").asBoolean()).isTrue();
            }
            String ref = operation.get("responses").get("200").get("content").get("application/json").get("schema").get("$ref").asText();
            var schema = spec.at(ref.substring(1));
            assertThat(schema.get("properties").get("meses").get("maxItems").asInt()).isEqualTo(24);
            var totales = spec.at(schema.get("properties").get("totales").get("$ref").asText().substring(1));
            totales.get("properties").properties().forEach(field -> {
                if (!Set.of("facturas", "movimientos").contains(field.getKey())) {
                    assertThat(field.getValue().get("type").asText()).isEqualTo("string");
                    assertThat(field.getValue().get("pattern").asText()).isEqualTo("^-?[0-9]+\\.[0-9]{2}$");
                }
            });
        }
    }

    @Test
    void informesDeEstadosDocumentanCategoriasCompletasParaGraficos() {
        for (String tipo : Set.of("obras", "presupuestos")) {
            var operation=spec.get("paths").get("/empresas/{empresa}/informes/"+tipo+"-por-estado").get("get");
            assertThat(operation.get("parameters")).hasSize(3);
            operation.get("parameters").forEach(parameter -> assertThat(parameter.get("required").asBoolean()).isTrue());
            String ref=operation.get("responses").get("200").get("content").get("application/json").get("schema").get("$ref").asText();
            var informe=spec.at(ref.substring(1));
            assertThat(informe.get("additionalProperties").asBoolean()).isFalse();
            assertThat(informe.get("properties").get("estados").get("minItems").asInt()).isEqualTo(1);
            var estado=spec.at(informe.get("properties").get("estados").get("items").get("$ref").asText().substring(1));
            assertThat(estado.get("required")).hasSize(5);
            assertThat(estado.get("properties").get("cantidad").get("minimum").asInt()).isZero();
        }
    }

    @Test
    void descargaSeDocumentaComoBinarioPrivadoConErroresJson() {
        var responses = spec.get("paths").get("/empresas/{empresa}/obras/{obra}/documentos/{id}/contenido")
                .get("get").get("responses");
        var success = responses.get("200");
        assertThat(success.get("content").has("application/octet-stream")).isTrue();
        assertThat(success.get("content").has("application/json")).isFalse();
        assertThat(success.get("headers").get("Cache-Control").get("schema").get("const").asText()).isEqualTo("no-store");
        assertThat(responses.get("404").get("content").get("application/json").get("schema").get("$ref").asText())
                .isEqualTo("#/components/schemas/Error");
    }

    @Test
    void rolesPropiosDocumentaJwtRespuestaYAusenciaDeIdDeUsuario() {
        var operation = spec.get("paths").get("/auth/me/roles").get("get");
        assertThat(operation.get("parameters")).isNull();
        assertThat(operation.get("security").get(0).has("bearerAuth")).isTrue();
        assertThat(operation.get("responses").has("200")).isTrue();
        assertThat(operation.get("responses").has("401")).isTrue();
        assertThat(operation.at("/responses/200/content/application~1json/schema/type").asText()).isEqualTo("array");
        assertThat(operation.at("/responses/200/content/application~1json/schema/items/$ref").asText())
                .isEqualTo("#/components/schemas/RolPropio");
        var schema = spec.at("/components/schemas/RolPropio");
        assertThat(schema.get("required")).hasSize(4);
        assertThat(schema.get("properties").has("empresa")).isTrue();
        assertThat(schema.get("properties").has("rol")).isTrue();
        assertThat(schema.get("properties").has("nombre")).isTrue();
        assertThat(schema.get("properties").has("global")).isTrue();
    }
}
