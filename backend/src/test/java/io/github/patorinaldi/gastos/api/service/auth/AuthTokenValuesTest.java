package io.github.patorinaldi.gastos.api.service.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenValuesTest {

    private final AuthTokenValues values = new AuthTokenValues();

    @Test
    void unTokenNuevoTieneElFormatoYEntraEnUnEnlaceSinEscaparse() {
        String token = values.generate();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(values.isWellFormed(token)).isTrue();
    }

    @Test
    void dosTokensNuevosSonDistintos() {
        assertThat(values.generate()).isNotEqualTo(values.generate());
    }

    @Test
    void loQueNoTieneElFormatoNoEsUnToken() {
        String token = values.generate();

        assertThat(values.isWellFormed(null)).isFalse();
        assertThat(values.isWellFormed(token.substring(1))).isFalse();
        assertThat(values.isWellFormed(token + "A")).isFalse();
        assertThat(values.isWellFormed(token.substring(1) + "="))
                .as("con relleno de base64").isFalse();
        assertThat(values.isWellFormed(token.substring(1) + "+"))
                .as("base64 común en lugar de base64url").isFalse();
    }

    // El check auth_tokens_hash_length exige exactamente 32 bytes.
    @Test
    void elHashEsUnSha256DeTreintaYDosBytes() {
        String token = values.generate();

        assertThat(values.hash(token)).hasSize(32).isEqualTo(values.hash(token));
        assertThat(values.hash(token)).isNotEqualTo(values.hash(values.generate()));
    }
}
