package com.empresa.api;

import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/** Comprueba el cableado real y los tipos REST, incluidos DTO anidados y genericos. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ArquitecturaTests extends PostgreSqlIntegrationTest {
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    private Set<Class<?>> controladores() {
        Set<Class<?>> classes = new HashSet<>();
        mappings.getHandlerMethods().values().forEach(handler -> {
            if (handler.getBeanType().getPackageName().startsWith("com.empresa.api")) {
                classes.add(handler.getBeanType());
            }
        });
        assertThat(classes).isNotEmpty();
        return classes;
    }

    @Test
    void controladoresInyectanServiciosSinAccederDirectamenteAPersistencia() {
        for (var controller : controladores()) {
            for (var constructor : controller.getDeclaredConstructors()) {
                for (Class<?> dependency : constructor.getParameterTypes()) { verificarServicio(dependency); }
            }
            for (var field : controller.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) { verificarServicio(field.getType()); }
            }
        }
    }

    private static void verificarServicio(Class<?> type) {
        assertThat(type.getPackageName()).startsWith("com.empresa.api");
        assertThat(AnnotatedElementUtils.hasAnnotation(type, Service.class))
                .as("Dependencia de controlador: %s", type.getName()).isTrue();
    }

    @Test
    void entradasYSalidasRestSonTiposExplicitosSinEntidadesJpa() {
        var controllers = controladores();
        mappings.getHandlerMethods().values().forEach(handler -> {
            if (!controllers.contains(handler.getBeanType())) { return; }
            var method = handler.getMethod();
            verificarDto(ResolvableType.forMethodReturnType(method, handler.getBeanType()), new HashSet<>());
            for (int index = 0; index < method.getParameterCount(); index++) {
                if (method.getParameters()[index].isAnnotationPresent(RequestBody.class)) {
                    verificarDto(ResolvableType.forMethodParameter(method, index, handler.getBeanType()), new HashSet<>());
                }
            }
        });
    }

    @Test
    void dtoInicialesTampocoArrastranEntidadesNiComponentesSpring() throws Exception {
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:com/empresa/api/dto/*.class");
        assertThat(resources).isNotEmpty();
        var readers = new CachingMetadataReaderFactory();
        for (var resource : resources) {
            String name = readers.getMetadataReader(resource).getClassMetadata().getClassName();
            if (!name.endsWith("package-info")) {
                verificarDto(ResolvableType.forClass(Class.forName(name)), new HashSet<>());
            }
        }
    }

    private static void verificarDto(ResolvableType type, Set<String> visited) {
        Class<?> raw = type.resolve();
        assertThat(raw).as("Tipo resoluble: %s", type).isNotNull().isNotEqualTo(Object.class);
        assertThat(type.hasUnresolvableGenerics()).as("Contrato generico explicito: %s", type).isFalse();
        assertThat(raw.isAnnotationPresent(Entity.class)).as("Entidad expuesta: %s", type).isFalse();
        assertThat(raw.getPackageName()).doesNotStartWith("com.empresa.api.model")
                .doesNotStartWith("jakarta.persistence").doesNotStartWith("org.hibernate");
        assertThat(AnnotatedElementUtils.hasAnnotation(raw, org.springframework.stereotype.Component.class))
                .as("Componente expuesto: %s", type).isFalse();
        if (!visited.add(type.toString())) { return; }
        if (type.isArray()) { verificarDto(type.getComponentType(), visited); }
        for (var argument : type.getGenerics()) { verificarDto(argument, visited); }
        if (raw.isEnum() || !raw.getPackageName().startsWith("com.empresa.api")) { return; }
        for (var field : raw.getDeclaredFields()) {
            if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers())) {
                verificarDto(ResolvableType.forField(field, type), visited);
            }
        }
    }

    // Regresion de la propia guarda: una entidad oculta tras pagina/lista tambien falla.
    record RespuestaInvalida(List<com.empresa.api.common.Pagina<com.empresa.api.model.Cliente>> items) {}
    record RespuestaOpaca(Object dato) {}

    @Test
    void guardaDetectaEntidadesAnidadasYRespuestasOpacas() {
        assertThatThrownBy(() -> verificarDto(ResolvableType.forClass(RespuestaInvalida.class), new HashSet<>()))
                .isInstanceOf(AssertionError.class);
        assertThatThrownBy(() -> verificarDto(ResolvableType.forClass(RespuestaOpaca.class), new HashSet<>()))
                .isInstanceOf(AssertionError.class);
    }
}
