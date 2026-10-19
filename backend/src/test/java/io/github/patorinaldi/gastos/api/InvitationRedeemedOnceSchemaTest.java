package io.github.patorinaldi.gastos.api;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica lo que la migración V7 le exige a la base: una invitación canjeada no se vuelve a
 * canjear ni se modifica su canje (RN-05).
 *
 * <p>Se escribe con SQL directo a propósito, igual que {@code HouseholdSwitchAndAccessSchemaTest}:
 * lo que se prueba es lo que sostiene la base aunque alguien escriba sin pasar por la aplicación.
 */
class InvitationRedeemedOnceSchemaTest extends HouseholdTestSupport {

    private UUID casaDeJuan;
    private UUID casaDeAna;
    private UUID juan;
    private UUID ana;
    private UUID carla;
    private UUID invitation;

    @BeforeEach
    void createRedeemedInvitation() {
        casaDeJuan = createHousehold("Casa de Juan");
        juan = createUser(casaDeJuan, "v7-" + UUID.randomUUID() + "@example.test", "Juan");
        casaDeAna = createHousehold("Casa de Ana");
        ana = createUser(casaDeAna, "v7-" + UUID.randomUUID() + "@example.test", "Ana");
        carla = createUser(casaDeAna, "v7-" + UUID.randomUUID() + "@example.test", "Carla");

        invitation = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into household_invitations (id, household_id, created_by, code_hash)"
                        + " values (?, ?, ?, sha256(convert_to(?, 'UTF8')))",
                invitation, casaDeJuan, juan, invitation.toString());
        jdbcTemplate.update(
                "update household_invitations set redeemed_by = ?, redeemed_at = now() where id = ?",
                ana, invitation);
    }

    @AfterEach
    void cleanUp() {
        deleteHousehold(casaDeJuan);
        deleteHousehold(casaDeAna);
    }

    @Test
    void anotherUserCannotRedeemItAgain() {
        assertRejected("update household_invitations set redeemed_by = ?, redeemed_at = now() where id = ?",
                carla, invitation);

        assertThat(jdbcTemplate.queryForObject(
                "select redeemed_by from household_invitations where id = ?", UUID.class, invitation))
                .isEqualTo(ana);
    }

    @Test
    void theRedemptionTimeCannotChange() {
        assertRejected("update household_invitations set redeemed_at = redeemed_at + interval '1 day' where id = ?",
                invitation);
    }

    // Devolverla a vigente permitiría canjearla de nuevo.
    @Test
    void itCannotBeMadeAvailableAgain() {
        assertRejected("update household_invitations set redeemed_by = null, redeemed_at = null where id = ?",
                invitation);
    }

    // Hibernate escribe la fila entera, canje incluido, aunque no haya cambiado.
    @Test
    void rewritingTheSameValuesIsAllowed() {
        assertThatCode(() -> jdbcTemplate.update(
                "update household_invitations set redeemed_by = redeemed_by, redeemed_at = redeemed_at"
                        + " where id = ?", invitation))
                .doesNotThrowAnyException();
    }

    private void assertRejected(String sql, Object... args) {
        assertThatThrownBy(() -> jdbcTemplate.update(sql, args))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("household_invitations_redeemed_once");
    }
}
