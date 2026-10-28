package io.github.patorinaldi.gastos.api.config;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebUrlPropertiesTest {

    @Test
    void armaElEnlaceSobreLaDireccionBase() {
        WebUrlProperties web = new WebUrlProperties(URI.create("https://gastos.ejemplo"));

        assertThat(web.link("/reset-password#token=abc"))
                .isEqualTo("https://gastos.ejemplo/reset-password#token=abc");
    }

    @Test
    void unaBarraFinalEnLaDireccionNoDuplicaLaDelCamino() {
        WebUrlProperties web = new WebUrlProperties(URI.create("https://gastos.ejemplo/"));

        assertThat(web.link("/reset-password")).isEqualTo("https://gastos.ejemplo/reset-password");
        assertThat(web.link("reset-password")).isEqualTo("https://gastos.ejemplo/reset-password");
    }

    @Test
    void aceptaElServidorDeDesarrolloConPuerto() {
        WebUrlProperties web = new WebUrlProperties(URI.create("http://localhost:5173"));

        assertThat(web.link("/reset-password")).isEqualTo("http://localhost:5173/reset-password");
    }

    // Cada una daría enlaces rotos: mejor que la aplicación no arranque.
    @ParameterizedTest
    @ValueSource(strings = {
            "gastos.pages.dev",
            "ftp://gastos.ejemplo",
            "https://gastos.ejemplo/?a=b",
            "https://gastos.ejemplo/#inicio",
            "mailto:ana@ejemplo.com"})
    void rechazaLoQueNoEsUnaDireccionWebAbsoluta(String value) {
        assertThatThrownBy(() -> new WebUrlProperties(URI.create(value)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WEB_URL");
    }
}
