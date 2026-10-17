package io.github.patorinaldi.gastos.api.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressTest {

    @Test
    void quitaElPuertoDeUnaIpv4() {
        assertThat(ClientAddress.withoutPort("203.0.113.7:54321")).isEqualTo("203.0.113.7");
    }

    @Test
    void quitaElPuertoYLosCorchetesDeUnaIpv6() {
        assertThat(ClientAddress.withoutPort("[2001:db8::1]:54321")).isEqualTo("2001:db8::1");
    }

    @Test
    void dejaIgualUnaDireccionSinPuerto() {
        assertThat(ClientAddress.withoutPort("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(ClientAddress.withoutPort("2001:db8::1")).isEqualTo("2001:db8::1");
    }

    // Sin corchetes, el último grupo de una IPv6 no se distingue de un puerto.
    @Test
    void noTocaUnaIpv6SinCorchetes() {
        assertThat(ClientAddress.withoutPort("2001:db8::1:8080")).isEqualTo("2001:db8::1:8080");
    }
}