package com.empresa.api.gestion;

import com.empresa.api.common.ApiException;
import com.empresa.api.model.*;
import com.empresa.api.security.AutorizacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GestionContactosObraServiceTest {

    static class StubGestionRepository extends GestionRepository {
        boolean duplicado = false;
        boolean tieneOtroResponsable = false;
        Obra obraStub;
        Contacto contactoStub;
        ObraContacto obraContactoStub;
        ObraContacto guardado;

        StubGestionRepository() {
            super(null, null, null);
        }

        @Override
        public void serializar() {
            // no-op para pruebas unitarias
        }

        @Override
        public Obra obra(int empresa, int id) {
            return obraStub;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T referencia(Class<T> clase, int id) {
            if (clase.equals(Contacto.class)) return (T) contactoStub;
            if (clase.equals(Usuario.class)) {
                var u = new Usuario();
                u.setId(id);
                return (T) u;
            }
            return null;
        }

        @Override
        public ObraContacto contactoObra(int obra, int id) {
            return obraContactoStub;
        }

        @Override
        public boolean contactoObraDuplicado(int obra, int contacto, String rol, int excluir) {
            return duplicado;
        }

        @Override
        public boolean obraTieneOtroResponsable(int obra, int excluirRelacion) {
            return tieneOtroResponsable;
        }

        @Override
        public void guardar(Object o) {
            if (o instanceof ObraContacto oc) {
                guardado = oc;
                oc.setId(999);
            }
        }

        @Override
        public void auditar(int usuario, int empresa, String entidad, int entidadId, String accion, Map<String, ?> antes, Map<String, ?> despues) {
            // no-op para pruebas unitarias
        }
    }

    static class StubAutorizacionService extends AutorizacionService {
        StubAutorizacionService() {
            super(null);
        }

        @Override
        public void exigirLectura(int usuario, int empresa, String permiso) {}

        @Override
        public void exigirEscritura(int usuario, int empresa, String permiso) {}
    }

    private StubGestionRepository repository;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC);
    private GestionContactosObraService service;

    @BeforeEach
    void setUp() {
        repository = new StubGestionRepository();
        service = new GestionContactosObraService(repository, new StubAutorizacionService(), clock);
    }

    @Test
    void esRolResponsableReconoceVariantesValidasYRechazaOtrosRoles() {
        assertThat(GestionContactosObraService.esRolResponsable("Responsable")).isTrue();
        assertThat(GestionContactosObraService.esRolResponsable("responsable")).isTrue();
        assertThat(GestionContactosObraService.esRolResponsable("  RESPONSABLE  ")).isTrue();
        assertThat(GestionContactosObraService.esRolResponsable("Responsable de obra")).isTrue();
        assertThat(GestionContactosObraService.esRolResponsable("RESPONSABLE DE OBRA")).isTrue();

        assertThat(GestionContactosObraService.esRolResponsable("Coordinador")).isFalse();
        assertThat(GestionContactosObraService.esRolResponsable("Técnico")).isFalse();
        assertThat(GestionContactosObraService.esRolResponsable("Contacto")).isFalse();
        assertThat(GestionContactosObraService.esRolResponsable("")).isFalse();
        assertThat(GestionContactosObraService.esRolResponsable("   ")).isFalse();
        assertThat(GestionContactosObraService.esRolResponsable(null)).isFalse();
    }

    @Test
    void asignarRechazaSegundaDesignacionResponsableSiLaObraYaTieneUna() {
        int usuario = 1;
        int empresa = 1;
        int obraId = 1001;
        int contactoId = 205;

        var entidad = new Entidad();
        entidad.setId(50);
        entidad.setActivo(true);

        var cliente = new Cliente();
        cliente.setId(10);
        cliente.setEntidad(entidad);

        var obra = new Obra();
        obra.setId(obraId);
        obra.setCliente(cliente);

        var contacto = new Contacto();
        contacto.setId(contactoId);
        contacto.setEntidad(entidad);
        contacto.setActivo(true);

        repository.obraStub = obra;
        repository.contactoStub = contacto;
        repository.duplicado = false;
        repository.tieneOtroResponsable = true; // Incluye otra relación del mismo contacto con el otro rol válido.

        var datos = new GestionContactosObraDtos.Asignar(contactoId, "Responsable", "Nota prueba");

        assertThatThrownBy(() -> service.asignar(usuario, empresa, obraId, datos))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("La obra ya tiene un contacto designado como responsable");

        assertThat(repository.guardado).isNull();
    }

    @Test
    void asignarPermiteResponsableSiNoExisteOtroPrevio() {
        int usuario = 1;
        int empresa = 1;
        int obraId = 1001;
        int contactoId = 205;

        var entidad = new Entidad();
        entidad.setId(50);
        entidad.setActivo(true);

        var cliente = new Cliente();
        cliente.setId(10);
        cliente.setEntidad(entidad);

        var obra = new Obra();
        obra.setId(obraId);
        obra.setCliente(cliente);

        var contacto = new Contacto();
        contacto.setId(contactoId);
        contacto.setEntidad(entidad);
        contacto.setActivo(true);

        repository.obraStub = obra;
        repository.contactoStub = contacto;
        repository.duplicado = false;
        repository.tieneOtroResponsable = false;

        var datos = new GestionContactosObraDtos.Asignar(contactoId, "Responsable", "Nota prueba");

        var relacion = service.asignar(usuario, empresa, obraId, datos);
        assertThat(relacion.contactoId()).isEqualTo(contactoId);
        assertThat(relacion.rol()).isEqualTo("Responsable");
        assertThat(repository.guardado).isNotNull();
    }

    @Test
    void editarRechazaCambiarAResponsableSiOtroContactoYaTieneElRol() {
        int usuario = 1;
        int empresa = 1;
        int obraId = 1001;
        int contactoId = 205;
        int relacionId = 77;

        var entidad = new Entidad();
        entidad.setId(50);
        entidad.setActivo(true);

        var cliente = new Cliente();
        cliente.setId(10);
        cliente.setEntidad(entidad);

        var obra = new Obra();
        obra.setId(obraId);
        obra.setCliente(cliente);

        var contacto = new Contacto();
        contacto.setId(contactoId);
        contacto.setEntidad(entidad);
        contacto.setActivo(true);

        var relacion = new ObraContacto();
        relacion.setId(relacionId);
        relacion.setObra(obra);
        relacion.setContacto(contacto);
        relacion.setRolEnObra("Técnico");

        repository.obraStub = obra;
        repository.contactoStub = contacto;
        repository.obraContactoStub = relacion;
        repository.duplicado = false;
        repository.tieneOtroResponsable = true; // Otro contacto ya es responsable

        var datos = new GestionContactosObraDtos.Editar("Responsable de obra", "Actualización");

        assertThatThrownBy(() -> service.editar(usuario, empresa, obraId, relacionId, datos))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("La obra ya tiene un contacto designado como responsable");
    }

    @Test
    void editarPermiteActualizarMismoResponsableSinConflicto() {
        int usuario = 1;
        int empresa = 1;
        int obraId = 1001;
        int contactoId = 205;
        int relacionId = 77;

        var entidad = new Entidad();
        entidad.setId(50);
        entidad.setActivo(true);

        var cliente = new Cliente();
        cliente.setId(10);
        cliente.setEntidad(entidad);

        var obra = new Obra();
        obra.setId(obraId);
        obra.setCliente(cliente);

        var contacto = new Contacto();
        contacto.setId(contactoId);
        contacto.setEntidad(entidad);
        contacto.setActivo(true);

        var relacion = new ObraContacto();
        relacion.setId(relacionId);
        relacion.setObra(obra);
        relacion.setContacto(contacto);
        relacion.setRolEnObra("Responsable");

        repository.obraStub = obra;
        repository.contactoStub = contacto;
        repository.obraContactoStub = relacion;
        repository.duplicado = false;
        repository.tieneOtroResponsable = false; // No hay OTRO responsable

        var datos = new GestionContactosObraDtos.Editar("Responsable", "Notas actualizadas");

        var resultado = service.editar(usuario, empresa, obraId, relacionId, datos);
        assertThat(resultado.rol()).isEqualTo("Responsable");
        assertThat(resultado.observaciones()).isEqualTo("Notas actualizadas");
    }
}
