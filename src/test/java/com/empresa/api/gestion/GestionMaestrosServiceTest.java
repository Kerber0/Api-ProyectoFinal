package com.empresa.api.gestion;

import com.empresa.api.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GestionMaestrosServiceTest {
    // Fixtures sintéticos deterministas; los checksums se calcularon localmente y no proceden de cuentas conocidas.
    private static final String IBAN_SINTETICO = "GB05TEST00000000005432";
    private static final String IBAN_SINTETICO_FORMATADO = "gb05 test 0000 0000 0054 32";

    @Test void normalizaYValidaIbanInternacional() {
        assertThat(GestionMaestrosService.iban(IBAN_SINTETICO_FORMATADO))
                .isEqualTo(IBAN_SINTETICO);
    }

    @Test void conservaAusenciaDeIban() {
        assertThat(GestionMaestrosService.iban(null)).isNull();
    }

    @Test void rechazaIbanConControlInvalido() {
        assertThatThrownBy(() -> GestionMaestrosService.iban("GB04TEST00000000005432"))
                .isInstanceOf(ApiException.class);
    }
}
