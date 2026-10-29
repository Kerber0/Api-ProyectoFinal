package com.empresa.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BaseDatosPostgreSqlTests extends PostgreSqlIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void distribucionIncluyeSoloLosDosSqlDefinitivos() {
        assertThat(getClass().getResource("/db/base de datos.sql")).isNotNull();
        assertThat(getClass().getResource("/db/base de datos de prueba.sql")).isNotNull();
    }

    @Test
    void esquemaConsolidadoContieneTodasLasAreas() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'", Integer.class)).isEqualTo(93);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.views WHERE table_schema='public'", Integer.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE NOT convalidated", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.api_facturacion') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.api_tesoreria') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.api_auditoria') IS NOT NULL", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.api_observaciones_obra') IS NOT NULL", Boolean.class)).isTrue();
    }

    @Test
    void camposAdministrativosClavesEIndicesEstanPresentes() {
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.columns WHERE table_schema='public' AND column_name='iban' ORDER BY table_name", String.class))
                .containsExactly("clientes", "operarios", "proveedores");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='public' AND c.contype='f'", Integer.class)).isGreaterThan(100);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_indexes WHERE schemaname='public'", Integer.class)).isEqualTo(435);
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.uk_factura_referencia') IS NOT NULL", Boolean.class)).isTrue();
    }

    @Test
    void catalogosYPermisosNecesariosSeCarganConLaEstructura() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Roles", Integer.class)).isEqualTo(13);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Permisos", Integer.class)).isEqualTo(32);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Tipos_IVA", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Formas_Pago", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Estados_Factura", Integer.class)).isEqualTo(10);
    }

    @Test
    void datosDePruebaSonSinteticosOpcionalesYSinUsuarios() {
        assertThat(jdbc.queryForObject("SELECT signature FROM dorna_demo_metadata WHERE id=1", String.class))
                .isEqualTo("dorna-demo-synthetic-2026-10-29");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Entidades WHERE nif_cif NOT LIKE 'DEMO-%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM Usuarios", Integer.class)).isZero();
    }
}
