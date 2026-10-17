package io.github.patorinaldi.gastos.api.service.household;

import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Códigos de invitación (RN-05): formato, normalización de lo que se tipea y hash que se guarda.
 */
class InvitationCodesTest {

    private final InvitationCodes codes = new InvitationCodes();

    // SHA-256 de "GST4K2P9XZ", calculado por fuera del código con sha256sum. Lo que se guarda en
    // household_invitations.code_hash: si la normalización cambiara, los códigos ya emitidos
    // dejarían de encontrarse.
    private static final String HASH_DE_GST_4K2P_9XZ =
            "1ecf2bdfe210840fe26007b9239eb2926cce3fc11dadd2ffcfe8e9d58c409f90";

    @Test
    void elCodigoGeneradoTieneElFormatoDelPrefijoYDosGrupos() {
        assertThat(codes.generate()).matches("GST-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{3}");
    }

    // Se dicta o se copia a mano: 0/O, 1/I/L no pueden aparecer nunca.
    @Test
    void nuncaUsaCaracteresQueSeConfundenAlLeerlos() {
        for (int i = 0; i < 2_000; i++) {
            assertThat(codes.generate()).doesNotContain("0", "O", "1", "I", "L");
        }
    }

    @Test
    void dosCodigosGeneradosSonDistintos() {
        Set<String> generados = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            generados.add(codes.generate());
        }

        assertThat(generados).hasSize(1_000);
    }

    @Test
    void elHashEsElSha256DelCodigoSinGuiones() {
        assertThat(HexFormat.of().formatHex(codes.hash("GST-4K2P-9XZ"))).isEqualTo(HASH_DE_GST_4K2P_9XZ);
    }

    // Quien recibe el código lo tipea como puede: en minúsculas, con espacios en lugar de guiones,
    // o con espacios de más al copiarlo de un chat.
    @ParameterizedTest
    @ValueSource(strings = {"gst-4k2p-9xz", "GST 4K2P 9XZ", "  GST-4K2P-9XZ  ", "GST4K2P9XZ", "Gst - 4k2p - 9Xz"})
    void loQueSeTipeaDeOtraFormaDaElMismoHash(String tipeado) {
        assertThat(HexFormat.of().formatHex(codes.hash(tipeado))).isEqualTo(HASH_DE_GST_4K2P_9XZ);
    }

    @Test
    void sinElPrefijoEsOtroCodigo() {
        assertThat(HexFormat.of().formatHex(codes.hash("4K2P-9XZ"))).isNotEqualTo(HASH_DE_GST_4K2P_9XZ);
    }
}
