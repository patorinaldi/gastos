package io.github.patorinaldi.gastos.api;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica lo que la migración V5 le exige a la base: que un usuario con gastos pueda cambiarse
 * de hogar sin que esos gastos dejen el hogar anterior (RN-18), el archivado de hogares, y las
 * tablas de acceso (RN-03, RN-05).
 *
 * <p>Se escribe con SQL directo a propósito, igual que {@code SoftDeleteSchemaTest}: lo que se
 * prueba es lo que sostiene la base aunque alguien escriba sin pasar por la aplicación.
 */
class HouseholdSwitchAndAccessSchemaTest extends HouseholdTestSupport {

    private UUID householdA;
    private UUID householdB;
    private UUID user;

    @BeforeEach
    void createHouseholds() {
        householdA = createHousehold("Casa A");
        householdB = createHousehold("Casa B");
        user = createUser(householdA, "v5-" + UUID.randomUUID() + "@example.test", "Ana");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from auth_tokens where user_id = ?", user);
        jdbcTemplate.update("delete from household_invitations where household_id in (?, ?)",
                householdA, householdB);
        jdbcTemplate.update("delete from machine_tokens where household_id in (?, ?)",
                householdA, householdB);
        // A primero: ahí están los gastos del usuario, que después puede estar en B.
        deleteHousehold(householdA);
        deleteHousehold(householdB);
    }

    @Test
    void switchingHouseholdLeavesExpensesInThePreviousOne() {
        UUID expense = insertExpense(householdA, user);

        // Con la FK compuesta de la V1 este update fallaba: el usuario tiene gastos en A.
        jdbcTemplate.update("update users set household_id = ? where id = ?", householdB, user);

        // El gasto sigue en A, con su responsable, y los que siguen en A lo ven.
        UUID owner = withHousehold(householdA, () -> jdbcTemplate.queryForObject(
                "select owner_id from expenses where id = ?", UUID.class, expense));
        assertThat(owner).isEqualTo(user);

        // El nombre del ex integrante se sigue resolviendo: users no tiene RLS.
        String ownerName = withHousehold(householdA, () -> jdbcTemplate.queryForObject(
                "select u.name from expenses e join users u on u.id = e.owner_id where e.id = ?",
                String.class, expense));
        assertThat(ownerName).isEqualTo("Ana");

        // Y desde B, su hogar nuevo, ese gasto no se ve.
        Integer visibleFromB = withHousehold(householdB, () -> jdbcTemplate.queryForObject(
                "select count(*) from expenses where id = ?", Integer.class, expense));
        assertThat(visibleFromB).isZero();
    }

    @Test
    void archivedHouseholdAlwaysHasItsArchiveDate() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update households set active = false where id = ?", householdA))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("households_active_archived_at_consistent");

        jdbcTemplate.update(
                "update households set active = false, archived_at = now() where id = ?", householdA);
        Boolean active = jdbcTemplate.queryForObject(
                "select active from households where id = ?", Boolean.class, householdA);
        assertThat(active).isFalse();
    }

    @Test
    void authTokensStoreOnlyAHashAndExpireAfter24Hours() {
        // Un token en claro no entra: la columna exige los 32 bytes de un SHA-256.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into auth_tokens (user_id, purpose, token_hash) values (?, ?, ?)",
                user, "email_verification", "token-en-claro".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("auth_tokens_hash_length");

        UUID token = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into auth_tokens (id, user_id, purpose, token_hash) values (?, ?, ?, ?)",
                token, user, "email_verification", sha256("token"));

        Boolean expiresIn24Hours = jdbcTemplate.queryForObject(
                "select expires_at - created_at = interval '24 hours' from auth_tokens where id = ?",
                Boolean.class, token);
        assertThat(expiresIn24Hours).isTrue();
    }

    @Test
    void invitationsExpireAfter7DaysAndRedemptionRecordsWhoAndWhen() {
        UUID invitation = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into household_invitations (id, household_id, created_by, code_hash)"
                        + " values (?, ?, ?, ?)",
                invitation, householdA, user, sha256("GST-4K2P-9XZ"));

        Boolean expiresIn7Days = jdbcTemplate.queryForObject(
                "select expires_at - created_at = interval '7 days'"
                        + " from household_invitations where id = ?",
                Boolean.class, invitation);
        assertThat(expiresIn7Days).isTrue();

        // Canjeada sin saber por quién no es un estado válido.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update household_invitations set redeemed_at = now() where id = ?", invitation))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("household_invitations_redemption_consistent");
    }

    @Test
    void machineTokensNeedANameAndStoreOnlyAHash() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into machine_tokens (household_id, user_id, name, token_hash)"
                        + " values (?, ?, ?, ?)",
                householdA, user, "   ", sha256("token")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("machine_tokens_name_not_blank");

        jdbcTemplate.update(
                "insert into machine_tokens (household_id, user_id, name, token_hash)"
                        + " values (?, ?, ?, ?)",
                householdA, user, "iPhone de Ana", sha256("token"));

        // El mismo hash no puede repetirse: autenticar por hash tiene que dar un único token.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into machine_tokens (household_id, user_id, name, token_hash)"
                        + " values (?, ?, ?, ?)",
                householdA, user, "Otro", sha256("token")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("machine_tokens_token_hash_key");
    }

    // ------------------------------------------------------------------

    private UUID insertExpense(UUID household, UUID owner) {
        UUID id = UUID.randomUUID();
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into expenses (id, household_id, owner_id, merchant, amount, expense_date,"
                        + " payment_method) values (?, ?, ?, ?, ?, ?, ?)",
                id, household, owner, "Carrefour", new BigDecimal("100.00"),
                LocalDate.of(2026, 9, 20), "Tarjeta"));
        return id;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
