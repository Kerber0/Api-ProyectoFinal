package com.empresa.api;

import com.empresa.api.model.FacturaCliente;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostgreSqlTests extends PostgreSqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;

    @Test
    void cuentaDeAplicacionNoPuedeCambiarEsquemaNiMarcaDePruebas() {
        assertThat(jdbc.queryForObject("SELECT rolsuper OR rolcreatedb OR rolcreaterole FROM pg_roles WHERE rolname=current_user", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT has_schema_privilege(current_user,'public','CREATE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user,'dorna_demo_metadata','INSERT,UPDATE,DELETE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT has_table_privilege(current_user,'dorna_demo_metadata','SELECT')", Boolean.class)).isTrue();
    }

    @Test
    void vencimientoDeSesionConservaInstanteConOtraZonaDeConexion() {
        int user = jdbc.queryForObject("INSERT INTO Usuarios(nombre_usuario,password_hash) VALUES ('test-zona','hash-ficticio') RETURNING id_usuario", Integer.class);
        var repository = new com.empresa.api.security.AccesoRepository(jdbc);
        var instante = java.time.Instant.parse("2026-10-25T01:30:00.123456Z");
        String session = java.util.UUID.randomUUID().toString();
        jdbc.execute("SET LOCAL TIME ZONE 'Europe/Madrid'");
        repository.crearSesion(session, user, "a".repeat(64), instante);
        jdbc.execute("SET LOCAL TIME ZONE 'Pacific/Auckland'");
        assertThat(repository.bloquearSesion(session).orElseThrow().expira()).isEqualTo(instante);
        assertThat(repository.sesionVigente(session, user, 0, instante.minusNanos(1000))).isTrue();
        assertThat(repository.sesionVigente(session, user, 0, instante)).isFalse();
    }

    @Test
    void motorRealYBaseConsolidadaSinUsuariosEnDatosDePrueba() {
        assertThat(jdbc.queryForObject("SHOW server_version_num", Integer.class)).isGreaterThanOrEqualTo(180000);
        assertThat(jdbc.queryForObject("SELECT signature FROM dorna_demo_metadata WHERE id=1", String.class))
                .isEqualTo("dorna-demo-synthetic-2026-10-29");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Usuarios", Integer.class)).isZero();
    }

    @Test
    void busquedaYUnicidadUsanCriterioEspanolPeroTokensSonBinarios() {
        assertThat(jdbc.queryForObject("SELECT 'ÁRBOL' COLLATE dorna_es_ci LIKE '%arbol%'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT 'año' COLLATE dorna_es_ci = 'ano'", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT 'a_b%' COLLATE dorna_es_ci LIKE 'a!_b!%' ESCAPE '!'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT 'ABC' COLLATE \"C\" = 'abc'", Boolean.class)).isFalse();
        Integer id = jdbc.queryForObject("INSERT INTO Roles(nombre) VALUES ('ÁRBOL prueba') RETURNING id_rol", Integer.class);
        assertThat(jdbc.queryForList("INSERT INTO Roles(nombre) VALUES ('arbol PRUEBA') ON CONFLICT DO NOTHING RETURNING id_rol", Integer.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT id_rol FROM Roles WHERE nombre='arbol prueba'", Integer.class)).isEqualTo(id);
    }

    @Test
    void identidadesNoColisionanConIdsExplicitosDeFixtures() {
        int max = jdbc.queryForObject("SELECT max(id_entidad) FROM Entidades", Integer.class);
        int id = jdbc.queryForObject("INSERT INTO Entidades(tipo_entidad,razon_social,nif_cif) VALUES ('EMPRESA','Identidad prueba','TEST-PG-IDENTIDAD') RETURNING id_entidad", Integer.class);
        assertThat(id).isGreaterThan(max);
    }

    @Test
    void textosJpaSeLeenYEscribenComoTextSinLargeObjectOid() {
        String texto = "Observación ñ y € ".repeat(1000);
        var factura = entities.find(FacturaCliente.class, 5001);
        factura.setObservaciones(texto);
        entities.flush(); entities.clear();
        assertThat(entities.find(FacturaCliente.class, 5001).getObservaciones()).isEqualTo(texto);
        assertThat(jdbc.queryForObject("SELECT pg_typeof(observaciones)::text FROM Facturas_Cliente WHERE id_factura=5001", String.class)).isEqualTo("text");
    }

    @Test
    void vistaBeneficioNoMultiplicaFacturasPartesNiMateriales() {
        for (int obra : jdbc.queryForList("SELECT id_obra FROM Obras", Integer.class)) {
            BigDecimal ingreso = jdbc.queryForObject("SELECT COALESCE(SUM(total),0) FROM Facturas_Cliente WHERE id_obra=?", BigDecimal.class, obra);
            BigDecimal mano = jdbc.queryForObject("SELECT COALESCE(SUM(coste_total),0) FROM Partes_Mano_Obra WHERE id_obra=?", BigDecimal.class, obra);
            BigDecimal material = jdbc.queryForObject("SELECT COALESCE(SUM(importe),0) FROM Materiales_Por_Obra WHERE id_obra=?", BigDecimal.class, obra);
            assertThat(jdbc.queryForObject("SELECT ingresos FROM vw_beneficio_obra WHERE id_obra=?", BigDecimal.class, obra)).isEqualByComparingTo(ingreso);
            assertThat(jdbc.queryForObject("SELECT gastos FROM vw_beneficio_obra WHERE id_obra=?", BigDecimal.class, obra)).isEqualByComparingTo(mano.add(material));
            assertThat(jdbc.queryForObject("SELECT beneficio FROM vw_beneficio_obra WHERE id_obra=?", BigDecimal.class, obra)).isEqualByComparingTo(ingreso.subtract(mano).subtract(material));
        }
    }

    @Test
    void fechaCalendarioYMicrosegundosSeConservanSinDependerDeZona() {
        jdbc.execute("SET LOCAL TIME ZONE 'Europe/Madrid'");
        LocalDate fecha = LocalDate.of(2026, 3, 29);
        jdbc.update("UPDATE Facturas_Cliente SET fecha=? WHERE id_factura=5001", fecha);
        assertThat(jdbc.queryForObject("SELECT fecha FROM Facturas_Cliente WHERE id_factura=5001", LocalDate.class)).isEqualTo(fecha);
        OffsetDateTime instante = OffsetDateTime.parse("2026-10-25T01:30:00.123456Z");
        assertThat(jdbc.queryForObject("SELECT ?::timestamptz", OffsetDateTime.class, instante).toInstant()).isEqualTo(instante.toInstant());
    }

    @Test
    void fechaModificacionSigueActualizandoseEnUtc() {
        jdbc.update("UPDATE Entidades SET fecha_modificacion='2000-01-01 00:00:00' WHERE id_entidad=101");
        jdbc.execute("SET LOCAL TIME ZONE 'Pacific/Auckland'");
        jdbc.update("UPDATE Entidades SET observaciones='Cambio PostgreSQL' WHERE id_entidad=101");
        LocalDateTime stored = jdbc.queryForObject("SELECT fecha_modificacion FROM Entidades WHERE id_entidad=101", LocalDateTime.class);
        LocalDateTime utc = jdbc.queryForObject("SELECT timezone('UTC', statement_timestamp())", LocalDateTime.class);
        assertThat(java.time.Duration.between(stored, utc).abs()).isLessThan(java.time.Duration.ofSeconds(5));
    }

    @Test
    void numericosMantienenPrecisionYBooleanosSonNativos() {
        assertThat(jdbc.queryForObject("SELECT 0.10::numeric + 0.20::numeric", BigDecimal.class)).isEqualByComparingTo("0.30");
        assertThat(jdbc.queryForObject("SELECT pg_typeof(activo)::text FROM Entidades LIMIT 1", String.class)).isEqualTo("boolean");
        jdbc.update("UPDATE Entidades SET activo=? WHERE id_entidad=101", false);
        assertThat(jdbc.queryForObject("SELECT activo FROM Entidades WHERE id_entidad=101", Boolean.class)).isFalse();
    }
}
