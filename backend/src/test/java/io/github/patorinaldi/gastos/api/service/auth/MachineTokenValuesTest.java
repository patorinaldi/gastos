package io.github.patorinaldi.gastos.api.service.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineTokenValuesTest {

    private final MachineTokenValues values = new MachineTokenValues();

    @Test
    void generaTokensConElFormatoQueAcepta() {
        String token = values.generate();

        assertThat(token).startsWith("gst_").hasSize(47);
        assertThat(values.isWellFormed(token)).isTrue();
    }

    @Test
    void cadaTokenEsDistinto() {
        assertThat(values.generate()).isNotEqualTo(values.generate());
    }

    @Test
    void rechazaLoQueNoTieneElFormato() {
        String token = values.generate();

        assertThat(values.isWellFormed(null)).isFalse();
        assertThat(values.isWellFormed("")).isFalse();
        assertThat(values.isWellFormed(token.substring(4))).isFalse();
        assertThat(values.isWellFormed("GST_" + token.substring(4))).isFalse();
        assertThat(values.isWellFormed(token + "A")).isFalse();
        assertThat(values.isWellFormed(token.substring(0, 46) + "+")).isFalse();
        assertThat(values.isWellFormed(" " + token)).isFalse();
    }

    // El hash tiene que entrar en machine_tokens_hash_length, que exige 32 bytes, y ser estable:
    // es por lo que se busca el token en cada petición.
    @Test
    void elHashEsDe32BytesYEstable() {
        String token = values.generate();

        assertThat(values.hash(token)).hasSize(32).isEqualTo(values.hash(token));
        assertThat(values.hash(token)).isNotEqualTo(values.hash(values.generate()));
    }
}