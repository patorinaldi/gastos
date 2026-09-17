package io.github.patorinaldi.gastos.api.security;

import io.github.patorinaldi.gastos.api.HouseholdTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el mecanismo: que el rol sea el restringido y que el listener propague el hogar a
 * Postgres. La cobertura sistemática del aislamiento —las tres tablas, las cuatro operaciones,
 * y que el hogar A no alcance datos de B ni conociendo sus ids— está en HouseholdIsolationTest.
 */
class HouseholdTransactionListenerTest extends HouseholdTestSupport {

    @AfterEach
    void clearHousehold() {
        CurrentHousehold.clear();
    }

    @Test
    void appConnectsWithRestrictedRole() {
        Map<String, Object> role = jdbcTemplate.queryForMap(
                "select rolname, rolsuper, rolbypassrls from pg_roles where rolname = current_user");

        assertThat(role)
                .containsEntry("rolname", "gastos_api")
                .containsEntry("rolsuper", false)
                .containsEntry("rolbypassrls", false);
    }

    @Test
    void transactionWithoutHouseholdLeavesItUnset() {
        assertThat(inTransaction(this::currentHousehold)).isNull();
    }

    @Test
    void transactionWithHouseholdSetsItInPostgres() {
        UUID household = UUID.randomUUID();
        CurrentHousehold.set(household);

        assertThat(inTransaction(this::currentHousehold)).isEqualTo(household);
    }

    @Test
    void rowLevelSecurityOnlyShowsRowsOfCurrentHousehold() {
        UUID householdA = createHouseholdWithCategory();
        UUID householdB = createHouseholdWithCategory();
        try {
            assertThat(withHousehold(householdA, () -> countCategories(householdA, householdB)))
                    .isEqualTo(1);
            assertThat(withoutHousehold(() -> countCategories(householdA, householdB)))
                    .isZero();
        } finally {
            deleteHousehold(householdA);
            deleteHousehold(householdB);
        }
    }

    private Integer countCategories(UUID householdA, UUID householdB) {
        return jdbcTemplate.queryForObject(
                "select count(*) from categories where household_id in (?, ?)",
                Integer.class, householdA, householdB);
    }

    private UUID createHouseholdWithCategory() {
        UUID household = createHousehold("Hogar de prueba");
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into categories (household_id, name) values (?, ?)", household, "Comida"));
        return household;
    }
}
