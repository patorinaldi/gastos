package io.github.patorinaldi.gastos.api.service.limit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddressKeyTest {

    @Test
    void laClaveDeUnaIpv6EsSuPrefijo64() {
        assertThat(AddressKey.of("2001:db8:1:2:aaaa:bbbb:cccc:dddd")).isEqualTo("2001:db8:1:2:0:0:0:0/64");
        assertThat(AddressKey.of("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(AddressKey.of("203.0.113.7")).isEqualTo("203.0.113.7");
    }
}
