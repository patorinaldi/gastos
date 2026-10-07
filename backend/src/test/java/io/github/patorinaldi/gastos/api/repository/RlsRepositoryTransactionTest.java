package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.HouseholdTestSupport;
import io.github.patorinaldi.gastos.api.domain.Category;
import io.github.patorinaldi.gastos.api.domain.CategoryRule;
import io.github.patorinaldi.gastos.api.security.CurrentHousehold;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los repositorios de tablas con RLS funcionan aunque quien los llama no haya abierto una
 * transacción.
 *
 * <p>Los métodos de consulta declarados en un repositorio no abren transacción por su cuenta, y
 * sin transacción {@code HouseholdTransactionListener} no fija el hogar: la consulta devolvería
 * cero filas sin ningún error. Por eso los tres repositorios son
 * {@code @Transactional(readOnly = true)} a nivel de interfaz. Estas pruebas llaman al repositorio
 * con el hogar en {@code CurrentHousehold} y sin ninguna transacción abierta, como lo haría un
 * servicio que se olvidó de la suya.
 */
class RlsRepositoryTransactionTest extends HouseholdTestSupport {

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private CategoryRuleRepository rules;

    @Autowired
    private ExpenseRepository expenses;

    private UUID casa;
    private UUID ana;

    @BeforeEach
    void sembrar() {
        casa = createHousehold("Casa");
        ana = createUser(casa, "rls-" + UUID.randomUUID() + "@ejemplo.test", "Ana");
        withHousehold(casa, () -> {
            jdbcTemplate.update("insert into categories (household_id, name) values (?, ?)", casa, "Comida");
            jdbcTemplate.update("insert into expenses (household_id, owner_id, merchant, amount, expense_date,"
                    + " payment_method) values (?, ?, 'Kiosco', 100, current_date, 'Efectivo')", casa, ana);
        });
    }

    @AfterEach
    void limpiar() {
        CurrentHousehold.clear();
        deleteHousehold(casa);
    }

    @Test
    void lasConsultasDeclaradasVenElHogarSinTransaccionDelServicio() {
        CurrentHousehold.set(casa);

        assertThat(categories.findAllByActiveTrueOrderByNameAsc()).extracting(Category::getName)
                .containsExactly("Comida");
        assertThat(expenses.findByOwnerId(ana)).hasSize(1);
        assertThat(rules.findAllByOrderByPatternAsc()).isEmpty();
    }

    // La anotación de la interfaz es de solo lectura, pero save conserva la transacción de
    // escritura de SimpleJpaRepository: si la heredara, Hibernate no escribiría nada.
    @Test
    void saveSigueEscribiendoSinTransaccionDelServicio() {
        CurrentHousehold.set(casa);
        UUID comida = categories.findByNameIgnoreCaseAndActiveTrue("comida").orElseThrow().getId();

        categories.save(new Category(casa, "Transporte"));
        rules.save(new CategoryRule(casa, "kiosco", comida));
        CurrentHousehold.clear();

        assertThat(withHousehold(casa, () -> jdbcTemplate.queryForObject(
                "select count(*) from categories where name = 'Transporte'", Integer.class))).isEqualTo(1);
        assertThat(withHousehold(casa, () -> jdbcTemplate.queryForObject(
                "select count(*) from category_rules where pattern = 'kiosco'", Integer.class))).isEqualTo(1);
    }

    // Sin hogar en contexto, la consulta sigue sin ver nada: la anotación abre la transacción,
    // pero el aislamiento lo sigue decidiendo Postgres (RNF-03).
    @Test
    void sinHogarEnContextoSigueSinVerNada() {
        CurrentHousehold.clear();

        assertThat(categories.findAllByActiveTrueOrderByNameAsc()).isEmpty();
        assertThat(expenses.findByOwnerId(ana)).isEmpty();
    }
}