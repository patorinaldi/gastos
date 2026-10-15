package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.IntegrationTest;
import io.github.patorinaldi.gastos.api.domain.Household;
import io.github.patorinaldi.gastos.api.domain.HouseholdInvitation;
import io.github.patorinaldi.gastos.api.domain.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invitaciones a un hogar (V5). La tabla no tiene RLS: al canjear, quien busca el código todavía no
 * integra el hogar que lo emitió. Por eso estas pruebas no activan ningún hogar.
 */
@Transactional
class HouseholdInvitationRepositoryTest extends IntegrationTest {

    @Autowired
    private HouseholdRepository households;

    @Autowired
    private UserRepository users;

    @Autowired
    private HouseholdInvitationRepository invitations;

    @Autowired
    private EntityManager entityManager;

    // Postgres guarda microsegundos: con la precisión de Instant.now() la comparación no daría.
    private final Instant vence = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);

    @Test
    void seEncuentraPorElHashDelCodigo() {
        Household casa = households.saveAndFlush(new Household("Casa de Ana"));
        User ana = users.saveAndFlush(new User(casa.getId(), "ana-inv@example.test", "hash", "Ana"));
        byte[] hash = hash(7);
        invitations.saveAndFlush(new HouseholdInvitation(casa.getId(), ana.getId(), hash, vence));
        entityManager.clear();

        // Otra instancia con el mismo contenido: se compara el valor, no la referencia.
        HouseholdInvitation leida = invitations.findByCodeHash(Arrays.copyOf(hash, hash.length)).orElseThrow();

        assertThat(leida.getHouseholdId()).isEqualTo(casa.getId());
        assertThat(leida.getCreatedBy()).isEqualTo(ana.getId());
        assertThat(leida.getExpiresAt()).isEqualTo(vence);
        assertThat(leida.getCreatedAt()).isNotNull();
        assertThat(leida.isRedeemed()).isFalse();
    }

    @Test
    void otroHashNoLaEncuentra() {
        Household casa = households.saveAndFlush(new Household("Casa de Ana"));
        User ana = users.saveAndFlush(new User(casa.getId(), "ana-otro@example.test", "hash", "Ana"));
        invitations.saveAndFlush(new HouseholdInvitation(casa.getId(), ana.getId(), hash(7), vence));
        entityManager.clear();

        assertThat(invitations.findByCodeHash(hash(8))).isEmpty();
    }

    // Quien canjea es de otro hogar: redeemed_by no pasa por el disparador de pertenencia.
    @Test
    void elCanjeSePersisteConQuienYCuando() {
        Household casa = households.saveAndFlush(new Household("Casa de Ana"));
        Household otra = households.saveAndFlush(new Household("Casa de Juan"));
        User ana = users.saveAndFlush(new User(casa.getId(), "ana-canje@example.test", "hash", "Ana"));
        User juan = users.saveAndFlush(new User(otra.getId(), "juan-canje@example.test", "hash", "Juan"));
        HouseholdInvitation invitacion = invitations.saveAndFlush(
                new HouseholdInvitation(casa.getId(), ana.getId(), hash(7), vence));
        Instant canje = Instant.now().truncatedTo(ChronoUnit.MICROS);

        invitacion.redeem(juan.getId(), canje);
        invitations.saveAndFlush(invitacion);
        entityManager.clear();

        HouseholdInvitation leida = invitations.findById(invitacion.getId()).orElseThrow();
        assertThat(leida.getRedeemedBy()).isEqualTo(juan.getId());
        assertThat(leida.getRedeemedAt()).isEqualTo(canje);
    }

    private static byte[] hash(int relleno) {
        byte[] hash = new byte[32];
        Arrays.fill(hash, (byte) relleno);
        return hash;
    }
}
