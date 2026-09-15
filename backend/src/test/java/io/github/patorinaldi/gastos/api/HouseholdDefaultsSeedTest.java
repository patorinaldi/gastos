package io.github.patorinaldi.gastos.api;

import io.github.patorinaldi.gastos.api.security.CurrentHousehold;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HouseholdDefaultsSeedTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final List<UUID> households = new ArrayList<>();

    @AfterEach
    void deleteHouseholds() {
        CurrentHousehold.clear();
        households.forEach(this::deleteHousehold);
    }

    @Test
    void seedsCategoriesAndRulesOfCurrentHousehold() {
        UUID household = createHousehold();

        inHousehold(household, this::seed);

        assertThat(inHousehold(household, () -> count("categories"))).isPositive();
        assertThat(inHousehold(household, () -> count("category_rules"))).isPositive();
        assertThat(inHousehold(household, () -> categoryOf("coto"))).isEqualTo("Supermercado");
    }

    @Test
    void eachHouseholdGetsItsOwnCatalog() {
        UUID householdA = createHousehold();
        UUID householdB = createHousehold();

        inHousehold(householdA, this::seed);
        assertThat(inHousehold(householdB, () -> count("categories"))).isZero();

        inHousehold(householdB, this::seed);
        assertThat(inHousehold(householdB, () -> count("categories")))
                .isEqualTo(inHousehold(householdA, () -> count("categories")));
    }

    @Test
    void failsWithoutHouseholdInContext() {
        assertThatThrownBy(() -> inTransaction(this::seed))
                .isInstanceOf(DataAccessException.class)
                .rootCause()
                .hasMessageContaining("app.current_household");
    }

    @Test
    void failsIfHouseholdIsAlreadySeeded() {
        UUID household = createHousehold();
        inHousehold(household, this::seed);

        assertThatThrownBy(() -> inHousehold(household, this::seed))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Void: seed_household_defaults() no devuelve nada, se invoca por su efecto.
    private Void seed() {
        jdbcTemplate.execute("select seed_household_defaults()");
        return null;
    }

    // Sin where: RLS ya limita el conteo al hogar en contexto.
    private int count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private String categoryOf(String pattern) {
        return jdbcTemplate.queryForObject("""
                select c.name
                from category_rules r
                join categories c on c.id = r.category_id
                where r.pattern = ?
                """, String.class, pattern);
    }

    // households no tiene RLS: se inserta sin hogar en contexto.
    private UUID createHousehold() {
        UUID household = UUID.randomUUID();
        jdbcTemplate.update("insert into households (id, name) values (?, ?)", household, "Hogar de prueba");
        households.add(household);
        return household;
    }

    // category_rules referencia a categories con restrict: primero se borran las reglas.
    private void deleteHousehold(UUID household) {
        inHousehold(household, () -> {
            jdbcTemplate.update("delete from category_rules where household_id = ?", household);
            return jdbcTemplate.update("delete from categories where household_id = ?", household);
        });
        jdbcTemplate.update("delete from households where id = ?", household);
    }

    private <T> T inHousehold(UUID household, Supplier<T> work) {
        CurrentHousehold.set(household);
        try {
            return inTransaction(work);
        } finally {
            CurrentHousehold.clear();
        }
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }
}
