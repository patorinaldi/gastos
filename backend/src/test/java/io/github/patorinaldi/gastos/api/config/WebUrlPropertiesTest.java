package io.github.patorinaldi.gastos.api.config;

import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
