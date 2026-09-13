package io.github.patorinaldi.gastos.api.security;

import io.github.patorinaldi.gastos.api.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class HouseholdTransactionListenerTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

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
            CurrentHousehold.set(householdA);
            assertThat(inTransaction(() -> countCategories(householdA, householdB))).isEqualTo(1);

            CurrentHousehold.clear();
            assertThat(inTransaction(() -> countCategories(householdA, householdB))).isZero();
        } finally {
            deleteHousehold(householdA);
            deleteHousehold(householdB);
        }
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    // app_current_household() y no current_setting: tras una transacción que fijó el
    // hogar, current_setting devuelve '' en esa conexión en lugar de null.
    private UUID currentHousehold() {
        return jdbcTemplate.queryForObject("select app_current_household()", UUID.class);
    }

    private Integer countCategories(UUID householdA, UUID householdB) {
        return jdbcTemplate.queryForObject(
                "select count(*) from categories where household_id in (?, ?)",
                Integer.class, householdA, householdB);
    }

    // households no tiene RLS; categories sí, así que se inserta con el hogar en contexto.
    private UUID createHouseholdWithCategory() {
        UUID household = UUID.randomUUID();
        jdbcTemplate.update("insert into households (id, name) values (?, ?)", household, "Hogar de prueba");
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into categories (household_id, name) values (?, ?)", household, "Comida"));
        return household;
    }

    private void deleteHousehold(UUID household) {
        withHousehold(household, () -> jdbcTemplate.update(
                "delete from categories where household_id = ?", household));
        jdbcTemplate.update("delete from households where id = ?", household);
    }

    private void withHousehold(UUID household, Supplier<Integer> work) {
        CurrentHousehold.set(household);
        try {
            inTransaction(work);
        } finally {
            CurrentHousehold.clear();
        }
    }
}