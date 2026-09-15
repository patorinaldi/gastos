package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.IntegrationTest;
import io.github.patorinaldi.gastos.api.domain.Category;
import io.github.patorinaldi.gastos.api.domain.CategoryRule;
import io.github.patorinaldi.gastos.api.domain.Expense;
import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import io.github.patorinaldi.gastos.api.security.CurrentHousehold;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * categories, category_rules y expenses sí están bajo RLS: sin un hogar activo, la política
 * {@code with check} rechaza el insert y la de {@code using} no devuelve filas.
 *
 * <p>El hogar se fija con {@link CurrentHousehold}, que es el mecanismo de producción — lo lee
 * HouseholdTransactionListener en {@code afterBegin}. Por eso el seed va en
 * {@code @BeforeTransaction}: con {@code @Transactional} a nivel de clase, cuando corre un
 * {@code @BeforeEach} la transacción ya está abierta y el listener ya leyó (y no encontró) el
 * hogar.
 *
 * <p>El hogar y el usuario se insertan con SQL directo y se borran a mano porque quedan
 * confirmados fuera de la transacción del test; todo lo que el test escribe después sí revierte.
 * Ninguna de esas dos tablas tiene RLS todavía, así que el seed no necesita contexto.
 *
 * <p>Estos tests verifican el mapeo, no el aislamiento. Comprobar que el hogar A no ve los datos
 * del B es la tarea 7.
 */
@Transactional
class ExpenseRepositoryTest extends IntegrationTest {

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private CategoryRuleRepository rules;

    @Autowired
    private ExpenseRepository expenses;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private UUID hogarId;
    private UUID duenoId;

    @BeforeTransaction
    void sembrarHogarYActivarContexto() {
        hogarId = UUID.randomUUID();
        duenoId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into households (id, name) values (?, ?)",
                hogarId, "Casa de prueba");
        jdbcTemplate.update(
                "insert into users (id, household_id, email, password_hash, name)"
                        + " values (?, ?, ?, ?, ?)",
                duenoId, hogarId, "duenio@example.test", "hash", "Dueño");
        CurrentHousehold.set(hogarId);
    }

    @AfterTransaction
    void limpiarSeed() {
        CurrentHousehold.clear();
        jdbcTemplate.update("delete from users where household_id = ?", hogarId);
        jdbcTemplate.update("delete from households where id = ?", hogarId);
    }

    @Test
    void persisteYRecuperaUnGastoCategorizado() {
        Category comida = categories.saveAndFlush(new Category(hogarId, "Comida"));
        Expense gasto = new Expense(hogarId, duenoId, "Carrefour",
                new BigDecimal("1234.56"), LocalDate.of(2026, 9, 13), PaymentMethod.TARJETA);
        gasto.setCategoryId(comida.getId());
        UUID id = expenses.saveAndFlush(gasto).getId();

        entityManager.clear();

        Expense leido = expenses.findById(id).orElseThrow();
        assertThat(leido.getMerchant()).isEqualTo("Carrefour");
        // numeric(12,2): el importe conserva la escala exacta, sin punto flotante de por medio
        assertThat(leido.getAmount()).isEqualByComparingTo("1234.56");
        assertThat(leido.getAmount().scale()).isEqualTo(2);
        assertThat(leido.getExpenseDate()).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(leido.getPaymentMethod()).isEqualTo(PaymentMethod.TARJETA);
        assertThat(leido.getCategoryId()).isEqualTo(comida.getId());
        assertThat(leido.getCreatedAt()).isNotNull();
        assertThat(leido.getUpdatedAt()).isNotNull();
    }

    @Test
    void elMedioDePagoSeGuardaConElValorEnEspanolQueAdmiteElCheck() {
        UUID id = expenses.saveAndFlush(new Expense(hogarId, duenoId, "Kiosco",
                new BigDecimal("500.00"), LocalDate.of(2026, 9, 13), PaymentMethod.EFECTIVO)).getId();

        // Se lee la columna cruda: si el converter guardara el nombre de la constante
        // ("EFECTIVO"), el check expenses_payment_method_valid habría rechazado el insert.
        String crudo = jdbcTemplate.queryForObject(
                "select payment_method from expenses where id = ?", String.class, id);

        assertThat(crudo).isEqualTo("Efectivo");
        entityManager.clear();
        assertThat(expenses.findById(id).orElseThrow().getPaymentMethod())
                .isEqualTo(PaymentMethod.EFECTIVO);
    }

    @Test
    void unGastoSinCategoriaCaeEnLaBandejaDeNoCategorizados() {
        Category comida = categories.saveAndFlush(new Category(hogarId, "Comida"));
        Expense sinCategoria = new Expense(hogarId, duenoId, "Comercio nuevo",
                new BigDecimal("100.00"), LocalDate.of(2026, 9, 10), PaymentMethod.TRANSFERENCIA);
        Expense categorizado = new Expense(hogarId, duenoId, "Carrefour",
                new BigDecimal("200.00"), LocalDate.of(2026, 9, 11), PaymentMethod.TARJETA);
        categorizado.setCategoryId(comida.getId());
        expenses.saveAndFlush(sinCategoria);
        expenses.saveAndFlush(categorizado);

        entityManager.clear();

        List<Expense> bandeja = expenses.findByCategoryIdIsNullOrderByExpenseDateDesc();

        assertThat(bandeja).extracting(Expense::getMerchant).containsExactly("Comercio nuevo");
    }

    @Test
    void filtraGastosPorRangoDeFechas() {
        expenses.saveAndFlush(new Expense(hogarId, duenoId, "Agosto",
                new BigDecimal("10.00"), LocalDate.of(2026, 8, 20), PaymentMethod.EFECTIVO));
        expenses.saveAndFlush(new Expense(hogarId, duenoId, "Septiembre",
                new BigDecimal("20.00"), LocalDate.of(2026, 9, 5), PaymentMethod.EFECTIVO));
        entityManager.clear();

        List<Expense> deSeptiembre = expenses.findByExpenseDateBetweenOrderByExpenseDateDesc(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(deSeptiembre).extracting(Expense::getMerchant).containsExactly("Septiembre");
    }

    @Test
    void persisteUnaReglaDeCategorizacionYLaBuscaIgnorandoMayusculas() {
        Category comida = categories.saveAndFlush(new Category(hogarId, "Comida"));
        rules.saveAndFlush(new CategoryRule(hogarId, "carrefour", comida.getId()));
        entityManager.clear();

        assertThat(rules.findByPatternIgnoreCase("CARREFOUR")).isPresent();
        assertThat(rules.findByCategoryId(comida.getId())).hasSize(1);
        assertThat(categories.findByNameIgnoreCase("comida")).isPresent();
    }
}
