package com.empresa.api;

import com.empresa.api.dto.CobroCreateDTO;
import com.empresa.api.model.Cliente;
import com.empresa.api.model.Obra;
import com.empresa.api.model.Presupuesto;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ApiApplicationTests extends PostgreSqlIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Validator validator;

    @BeforeEach
    void exigeBaseDePruebasConDatosFicticios() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("dorna_test");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM dorna_demo_metadata WHERE id = 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void cargaElContextoYConsultaTodasLasEntidadesContraPostgreSql() {
        var entities = entityManager.getMetamodel().getEntities();
        assertThat(entities).hasSize(63);
        for (var entity : entities) {
            entityManager.createQuery("select e from " + entity.getName() + " e", entity.getJavaType())
                    .setMaxResults(1)
                    .getResultList();
        }
    }

    @Test
    void identificaLasCuatroEmpresasComoEjemplosSinAfirmarAislamientoMultiempresa() {
        var names = jdbc.queryForList(
                "SELECT razon_social FROM Entidades WHERE nif_cif LIKE 'DEMO-EMP-%' ORDER BY id_entidad",
                String.class);
        assertThat(names).containsExactly(
                fixture("Dorna Metal [DEMO]", "Empresa Demostración 01 SL"),
                fixture("Dorna Solution [DEMO]", "Empresa Demostración 02 SL"),
                fixture("Anrod Latem [DEMO]", "Empresa Demostración 03 SL"),
                fixture("Keltor [DEMO]", "Empresa Demostración 04 SL"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM Usuarios", Integer.class)).isZero();
    }

    @Test
    void recuperaClienteDireccionYLineasDesdeLasRelacionesJpa() {
        var obra = entityManager.find(Obra.class, 1001);
        assertThat(obra.getCliente().getEntidad().getRazonSocial())
                .isEqualTo(fixture("Cliente Norte [DEMO]", "Cliente Ejemplo 001 SL"));
        assertThat(obra.getDireccion().getDireccion())
                .isEqualTo(fixture("Calle Ficticia 100 [DEMO]", "Calle Ejemplo 001"));

        var presupuesto = entityManager.find(Presupuesto.class, 4001);
        assertThat(presupuesto.getObra().getId()).isEqualTo(obra.getId());
        assertThat(presupuesto.getLineas()).hasSize(2);
        var subtotal = presupuesto.getLineas().stream()
                .map(linea -> linea.getImporte())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(subtotal).isEqualByComparingTo(presupuesto.getSubtotal());
        assertThat(presupuesto.getTotal()).isEqualByComparingTo("3630.00");
    }

    @Test
    void recuperaDatosIncompletosSinInventarContactosFechasODirecciones() {
        var cliente = entityManager.find(Cliente.class, 102);
        assertThat(cliente.getEntidad().getEmail()).isNull();
        assertThat(cliente.getEntidad().getContactos()).isEmpty();
        assertThat(cliente.getEntidad().getDirecciones()).isEmpty();
        assertThat(cliente.getFormaPago()).isNull();

        var obra = entityManager.find(Obra.class, 1003);
        assertThat(obra.getDireccion()).isNull();
        assertThat(obra.getFechaInicio()).isNull();
        assertThat(obra.getFechaFinPrevista()).isNull();
    }

    @Test
    void laSemillaIncluyeCobrosPendientesParcialesYCompletos() {
        // Verifica los ejemplos; no acredita un servicio de saldos, todavia inexistente.
        String sql = """
                SELECT f.total - COALESCE((SELECT SUM(c.importe) FROM Cobros c
                    WHERE c.id_factura = f.id_factura), 0)
                FROM Facturas_Cliente f WHERE f.id_factura = ?
                """;
        assertThat(jdbc.queryForObject(sql, BigDecimal.class, 5001)).isEqualByComparingTo("1210.00");
        assertThat(jdbc.queryForObject(sql, BigDecimal.class, 5002)).isEqualByComparingTo("1420.00");
        assertThat(jdbc.queryForObject(sql, BigDecimal.class, 5003)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM Cobros WHERE id_factura = 5002", Integer.class)).isEqualTo(2);
    }

    @Test
    void laSemillaIncluyePagosPendientesParcialesYCompletos() {
        String sql = """
                SELECT f.total - COALESCE((SELECT SUM(p.importe) FROM Pagos p
                    WHERE p.id_factura = f.id_factura), 0)
                FROM Facturas_Proveedor f WHERE f.id_factura = ?
                """;
        assertThat(jdbc.queryForObject(sql, BigDecimal.class, 6001)).isEqualByComparingTo("484.00");
        assertThat(jdbc.queryForObject(sql, BigDecimal.class, 6002)).isEqualByComparingTo("468.00");
        assertThat(jdbc.queryForObject(sql, BigDecimal.class, 6003)).isEqualByComparingTo("0.00");
    }

    @Test
    void distingueFechasDeMovimientoYVencimientoEnLosEjemplos() {
        assertThat(jdbc.queryForObject("""
                SELECT SUM(importe) FROM Cobros
                WHERE fecha >= '2026-10-01' AND fecha < '2026-11-01'
                """, BigDecimal.class)).isEqualByComparingTo("600.00");
        assertThat(jdbc.queryForObject("""
                SELECT fecha_vencimiento FROM Facturas_Cliente WHERE id_factura = 5002
                """, LocalDate.class)).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void postgreSqlRechazaUnCobroConFacturaInexistente() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO Cobros (id_factura, fecha, importe)
                VALUES (999999, '2026-10-01', 10.00)
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void jakartaValidationRechazaImporteNegativoEnCobro() {
        var dto = CobroCreateDTO.builder()
                .idFactura(5002)
                .fecha(LocalDate.of(2026, 10, 1))
                .importe(new BigDecimal("-1.00"))
                .build();
        assertThat(validator.validate(dto))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("importe"));
    }

}
