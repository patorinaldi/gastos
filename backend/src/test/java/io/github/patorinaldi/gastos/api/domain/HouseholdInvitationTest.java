package io.github.patorinaldi.gastos.api.domain;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Canje de una invitación (RN-05): registra quién y cuándo, y una sola vez.
 */
class HouseholdInvitationTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T12:00:00Z");

    private final HouseholdInvitation invitacion = new HouseholdInvitation(
            UUID.randomUUID(), UUID.randomUUID(), new byte[32], AHORA.plusSeconds(60));

    @Test
    void unaInvitacionNuevaNoEstaCanjeada() {
        assertThat(invitacion.isRedeemed()).isFalse();
        assertThat(invitacion.getRedeemedBy()).isNull();
        assertThat(invitacion.getRedeemedAt()).isNull();
    }

    @Test
    void canjearRegistraQuienYCuando() {
        UUID ana = UUID.randomUUID();

        invitacion.redeem(ana, AHORA);

        assertThat(invitacion.isRedeemed()).isTrue();
        assertThat(invitacion.getRedeemedBy()).isEqualTo(ana);
        assertThat(invitacion.getRedeemedAt()).isEqualTo(AHORA);
    }

    // El motor no impide sobrescribir un canje ya registrado (RN-05): lo sostiene la aplicación.
    @Test
    void noSePuedeCanjearDosVeces() {
        UUID ana = UUID.randomUUID();
        invitacion.redeem(ana, AHORA);

        assertThatThrownBy(() -> invitacion.redeem(UUID.randomUUID(), AHORA.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(invitacion.getRedeemedBy()).isEqualTo(ana);
        assertThat(invitacion.getRedeemedAt()).isEqualTo(AHORA);
    }

    @Test
    void venceAlLlegarASuFechaDeVencimiento() {
        assertThat(invitacion.isExpiredAt(AHORA)).isFalse();
        assertThat(invitacion.isExpiredAt(AHORA.plusSeconds(60))).isTrue();
        assertThat(invitacion.isExpiredAt(AHORA.plusSeconds(61))).isTrue();
    }
}
