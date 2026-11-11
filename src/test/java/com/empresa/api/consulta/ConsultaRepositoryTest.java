package com.empresa.api.consulta;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultaRepositoryTest {
    // Fixture sintético determinista: BBAN de prueba y checksum calculado localmente; no procede de una cuenta conocida.
    private static final String IBAN_SINTETICO = "GB05TEST00000000005432";

    @Test void enmascaraIbanSinExponerMasQueLosUltimosCuatroCaracteres() {
        assertThat(ConsultaRepository.enmascararIban(IBAN_SINTETICO))
                .isEqualTo("****5432");
        assertThat(ConsultaRepository.enmascararIban(null)).isNull();
    }

    @Test void obraDetalleConstructorDeCompatibilidadInicializaCamposResponsableComoNulos() {
        var cliente = new ConsultaDtos.ReferenciaCliente(1, "CLI001", "Cliente Prueba");
        var detalle = new ConsultaDtos.ObraDetalle(10, "OBR-001", "Obra Prueba", "Desc", "1000.00",
                null, null, null, "EN_CURSO", cliente, null);
        assertThat(detalle.id()).isEqualTo(10);
        assertThat(detalle.cliente().nombre()).isEqualTo("Cliente Prueba");
        assertThat(detalle.responsableContactoRelacionId()).isNull();
        assertThat(detalle.responsableContactoId()).isNull();
        assertThat(detalle.responsableNombre()).isNull();
        assertThat(detalle.responsableRol()).isNull();
    }

    @Test void obraDetalleConstructorCanonicoAsignaCamposResponsableCliente() {
        var cliente = new ConsultaDtos.ReferenciaCliente(1, "CLI001", "Cliente Prueba");
        var detalle = new ConsultaDtos.ObraDetalle(10, "OBR-001", "Obra Prueba", "Desc", "1000.00",
                null, null, null, "EN_CURSO", cliente, null,
                42, 105, "Juan Pérez", "Responsable de obra");
        assertThat(detalle.responsableContactoRelacionId()).isEqualTo(42);
        assertThat(detalle.responsableContactoId()).isEqualTo(105);
        assertThat(detalle.responsableNombre()).isEqualTo("Juan Pérez");
        assertThat(detalle.responsableRol()).isEqualTo("Responsable de obra");
    }
}
