package io.github.patorinaldi.gastos.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica lo que la migración V4 le exige a la base: la marca de baja lógica en gastos y
 * categorías (RN-16), la coherencia entre {@code active} y {@code deleted_at}, y la unicidad del
 * nombre de categoría solo entre las activas (RN-09).
 *
 * <p>Las bajas se hacen con SQL directo a propósito: lo que se prueba es lo que sostiene la base
 * aunque alguien escriba sin pasar por las entidades. El filtrado de las filas dadas de baja es de
 * la aplicación y está en {@code SoftDeleteRepositoryTest}.
 */
class SoftDeleteSchemaTest extends HouseholdTestSupport {

    private UUID household;
    private UUID owner;

    @BeforeEach
    void createHouseholdWithOwner() {
        household = createHousehold("Casa de prueba");
        // Email único: el usuario queda confirmado y un cleanUp fallido no debe romper el siguiente.
        owner = createUser(household, "baja-" + household + "@example.test", "Integrante");
    }

    @AfterEach
    void cleanUp() {
        deleteHousehold(household);
    }

    @Test
    void newCategoriesAndExpensesStartActive() {
        UUID category = insertCategory("Comida");
        UUID expense = insertExpense(category);

        assertThat(isActive("categories", category)).isTrue();
        assertThat(isActive("expenses", expense)).isTrue();
        assertThat(deletedAt("categories", category)).isNull();
        assertThat(deletedAt("expenses", expense)).isNull();
    }

    @Test
    void softDeleteRecordsWhenItHappened() {
        UUID category = insertCategory("Comida");
        UUID expense = insertExpense(category);

        softDelete("expenses", expense);
        softDelete("categories", category);

        assertThat(isActive("expenses", expense)).isFalse();
        assertThat(isActive("categories", category)).isFalse();
        assertThat(deletedAt("expenses", expense)).isNotNull();
        assertThat(deletedAt("categories", category)).isNotNull();
    }

    @Test
    void activeAndDeletedAtCannotContradictEachOther() {
        UUID category = insertCategory("Comida");
        UUID expense = insertExpense(category);

        // Dada de baja sin fecha de baja.
        assertThatThrownBy(() -> withHousehold(household, () -> jdbcTemplate.update(
                "update expenses set active = false where id = ?", expense)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("expenses_active_deleted_at_consistent");

        // Activa con fecha de baja.
        assertThatThrownBy(() -> withHousehold(household, () -> jdbcTemplate.update(
                "update categories set deleted_at = now() where id = ?", category)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("categories_active_deleted_at_consistent");
    }

    @Test
    void deletedCategoryReleasesItsName() {
        UUID old = insertCategory("Transporte");
        softDelete("categories", old);

        // Con el índice total de V1 este alta fallaba. El nombre se repite sin distinguir
        // mayúsculas, que es justamente lo que el índice compara.
        UUID renewed = insertCategory("transporte");

        assertThat(renewed).isNotEqualTo(old);
        assertThat(withHousehold(household, () -> jdbcTemplate.queryForObject(
                "select count(*) from categories where lower(name) = 'transporte'", Integer.class)))
                .isEqualTo(2);
    }

    @Test
    void twoActiveCategoriesStillCannotShareAName() {
        insertCategory("Comida");

        // La baja lógica no debe abrir la puerta a duplicados entre categorías activas.
        assertThatThrownBy(() -> insertCategory("comida"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("categories_household_name_key");
    }

    @Test
    void softDeletedCategoryKeepsItsExpenses() {
        UUID category = insertCategory("Salud");
        UUID expense = insertExpense(category);

        softDelete("categories", category);

        // La baja es una marca, no un borrado: la clave foránea sigue en pie y el gasto
        // conserva su categoría en el historial.
        UUID categoryOfExpense = withHousehold(household, () -> jdbcTemplate.queryForObject(
                "select category_id from expenses where id = ?", UUID.class, expense));
        assertThat(categoryOfExpense).isEqualTo(category);
    }

    @Test
    void deactivatingACategoryDeletesItsRules() {
        UUID category = insertCategory("Supermercado");
        UUID otherCategory = insertCategory("Hogar");
        insertRule("coto", category);
        insertRule("sodimac", otherCategory);

        softDelete("categories", category);

        // El trigger de la V4 vale por cualquier camino que dé de baja la categoría, no solo por
        // la entidad. Las reglas de las demás categorías no se tocan.
        assertThat(withHousehold(household, () -> jdbcTemplate.queryForList(
                "select pattern from category_rules", String.class)))
                .containsExactly("sodimac");
    }

    // ------------------------------------------------------------------

    private UUID insertCategory(String name) {
        UUID id = UUID.randomUUID();
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into categories (id, household_id, name) values (?, ?, ?)",
                id, household, name));
        return id;
    }

    private void insertRule(String pattern, UUID category) {
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into category_rules (household_id, pattern, category_id) values (?, ?, ?)",
                household, pattern, category));
    }

    private UUID insertExpense(UUID category) {
        UUID id = UUID.randomUUID();
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into expenses (id, household_id, owner_id, category_id, merchant, amount,"
                        + " expense_date, payment_method) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, household, owner, category, "Farmacity", new BigDecimal("100.00"),
                LocalDate.of(2026, 9, 22), "Tarjeta"));
        return id;
    }

    private void softDelete(String table, UUID id) {
        withHousehold(household, () -> jdbcTemplate.update(
                "update " + table + " set active = false, deleted_at = now(), updated_at = now()"
                        + " where id = ?", id));
    }

    private Boolean isActive(String table, UUID id) {
        return withHousehold(household, () -> jdbcTemplate.queryForObject(
                "select active from " + table + " where id = ?", Boolean.class, id));
    }

    private Object deletedAt(String table, UUID id) {
        return withHousehold(household, () -> jdbcTemplate.queryForObject(
                "select deleted_at from " + table + " where id = ?", Object.class, id));
    }
}
