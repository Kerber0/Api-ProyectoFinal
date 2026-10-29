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
}
