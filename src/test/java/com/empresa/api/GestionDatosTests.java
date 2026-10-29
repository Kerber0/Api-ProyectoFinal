package com.empresa.api;

import com.empresa.api.common.ApiException;
import com.empresa.api.gestion.GestionDatosDtos.ContactoEntrada;
import com.empresa.api.gestion.GestionDatosDtos.DireccionEntrada;
import com.empresa.api.gestion.GestionDatosDtos.TipoTitular;
import com.empresa.api.gestion.GestionDatosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GestionDatosTests extends PostgreSqlIntegrationTest {
    @Autowired GestionDatosService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    int admin;

    @BeforeEach
    void preparar() {
        admin = usuario("test-gestion-datos-admin", "ADMIN", null);
    }

    int usuario(String nombre, String rol, Integer empresa) {
        int id = jdbc.queryForObject(
                "INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES (?,?) RETURNING id_usuario",
                Integer.class, nombre, passwords.encode("clave-gestion-datos"));
        if (empresa == null) {
            jdbc.update("INSERT INTO Usuario_Rol(id_usuario,id_rol) SELECT ?,id_rol FROM Roles WHERE nombre=?", id, rol);
        } else {
            jdbc.update("INSERT INTO API_Accesos_Empresa(id_usuario,id_empresa,id_rol) SELECT ?,?,id_rol FROM Roles WHERE nombre=?",
                    id, empresa, rol);
        }
        return id;
    }

    ContactoEntrada contacto(String nombre, boolean principal, boolean activo) {
        return new ContactoEntrada(nombre, "Privado", "Responsable", "Obras", "900000000",
                "600000000", "privado@example.test", "Texto privado", principal, activo);
    }

    DireccionEntrada direccion(String calle, boolean principal, boolean activo) {
        return new DireccionEntrada("OBRA", calle, "28001", "Madrid", "Madrid", "España",
                "900000000", "Texto privado", principal, activo);
    }

    void error(Runnable accion, int estado) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.status()).isEqualTo(estado));
    }

    @Test
    void contactoSeCreaActualizaYConsultaEnSuTitular() {
        var creado = service.guardarContacto(admin, 1, TipoTitular.CLIENTE, 101, null,
                contacto("Contacto etapa once", false, true));
        var actualizado = service.guardarContacto(admin, 1, TipoTitular.CLIENTE, 101, creado.id(),
                contacto("Contacto actualizado", true, true));

        assertThat(actualizado.nombre()).isEqualTo("Contacto actualizado");
        assertThat(actualizado.principal()).isTrue();
        assertThat(service.contacto(admin, 1, TipoTitular.CLIENTE, 101, creado.id())).isEqualTo(actualizado);
        assertThat(service.contactos(admin, 1, TipoTitular.CLIENTE, 101))
                .extracting(c -> c.id()).contains(creado.id());
    }

    @Test
    void direccionSeCreaActualizaYConsultaEnSuTitular() {
        var creada = service.guardarDireccion(admin, 1, TipoTitular.PROVEEDOR, 201, null,
                direccion("Calle inicial", false, true));
        var actualizada = service.guardarDireccion(admin, 1, TipoTitular.PROVEEDOR, 201, creada.id(),
                direccion("Calle actualizada", true, true));

        assertThat(actualizada.direccion()).isEqualTo("Calle actualizada");
        assertThat(actualizada.principal()).isTrue();
        assertThat(service.direccion(admin, 1, TipoTitular.PROVEEDOR, 201, creada.id())).isEqualTo(actualizada);
        assertThat(service.direcciones(admin, 1, TipoTitular.PROVEEDOR, 201))
                .extracting(d -> d.id()).contains(creada.id());
    }

    @Test
    void nuevoPrincipalDesmarcaElAnteriorSinBorrarlo() {
        var primero = service.guardarContacto(admin, 1, TipoTitular.CLIENTE, 101, null,
                contacto("Principal uno", true, true));
        var segundo = service.guardarContacto(admin, 1, TipoTitular.CLIENTE, 101, null,
                contacto("Principal dos", true, true));

        assertThat(service.contacto(admin, 1, TipoTitular.CLIENTE, 101, primero.id()).principal()).isFalse();
        assertThat(service.contacto(admin, 1, TipoTitular.CLIENTE, 101, segundo.id()).principal()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM Contactos WHERE id_entidad=(SELECT id_entidad FROM Clientes WHERE id_cliente=101) AND principal=true",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void noAdmitePrincipalInactivoNiCruzaTitulares() {
        error(() -> service.guardarContacto(admin, 1, TipoTitular.CLIENTE, 101, null,
                contacto("Invalido", true, false)), 400);
        var creado = service.guardarContacto(admin, 1, TipoTitular.CLIENTE, 101, null,
                contacto("Solo cliente", false, true));
        error(() -> service.contacto(admin, 1, TipoTitular.CLIENTE, 102, creado.id()), 404);
        error(() -> service.guardarContacto(admin, 1, TipoTitular.PROVEEDOR, 201, creado.id(),
                contacto("Cruce", false, true)), 404);
    }

    @Test
    void maestroCompartidoExigeEscrituraEnTodasSusEmpresas() {
        int local = usuario("test-gestion-datos-local", "ADMIN", 1);
        error(() -> service.guardarDireccion(local, 1, TipoTitular.CLIENTE, 101, null,
                direccion("Calle no autorizada", false, true)), 404);
    }

    @Test
    void auditoriaNoCopiaDatosPersonalesNiTextoLibre() {
        var creado = service.guardarContacto(admin, 1, TipoTitular.CLIENTE, 101, null,
                contacto("Nombre muy privado", false, true));
        String cambios = jdbc.queryForObject(
                "SELECT cambios::text FROM API_Gestion_Auditoria WHERE entidad='CONTACTO' AND entidad_id=? ORDER BY id DESC LIMIT 1",
                String.class, creado.id());

        assertThat(cambios).contains("activo", "principal")
                .doesNotContain("Nombre muy privado", "privado@example.test", "Texto privado", "900000000");
    }

    @Test
    void operarioReutilizaContactosYDireccionesSinCruzarEmpresa() {
        var contacto = service.guardarContacto(admin, 1, TipoTitular.OPERARIO, 301, null,
                contacto("Referencia empleado", true, true));
        var direccion = service.guardarDireccion(admin, 1, TipoTitular.OPERARIO, 301, null,
                direccion("Calle empleado", true, true));
        assertThat(service.contacto(admin,1,TipoTitular.OPERARIO,301,contacto.id()).nombre())
                .isEqualTo("Referencia empleado");
        assertThat(service.direccion(admin,1,TipoTitular.OPERARIO,301,direccion.id()).direccion())
                .isEqualTo("Calle empleado");
        error(() -> service.contacto(admin,2,TipoTitular.OPERARIO,301,contacto.id()),404);
    }
}
