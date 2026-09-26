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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Baja lógica vista desde los repositorios (RN-16): {@code delete} marca la fila en lugar de
 * borrarla. Un gasto dado de baja no aparece en ninguna consulta por JPA. Una categoría dada de
 * baja deja de ofrecerse, pero el historial y el análisis la siguen viendo, y sus reglas se
 * eliminan.
 *
 * <p>El hogar y el usuario quedan confirmados fuera de la transacción del test, así que el email
 * es único por ejecución: si una limpieza falla, el siguiente test no choca con
 * {@code users_email_key}.
 *
 * <p>Lo que la base garantiza por su cuenta (el check de coherencia y el índice parcial) está en
 * {@code SoftDeleteSchemaTest}. El seed y el contexto de hogar siguen el mismo esquema que
 * {@link ExpenseRepositoryTest}.
 */
@Transactional
class SoftDeleteRepositoryTest extends IntegrationTest {

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
                duenoId, hogarId, "baja-" + hogarId + "@example.test", "hash", "Dueño");
        CurrentHousehold.set(hogarId);
    }

    @AfterTransaction
    void limpiarSeed() {
        CurrentHousehold.clear();
        jdbcTemplate.update("delete from users where household_id = ?", hogarId);
        jdbcTemplate.update("delete from households where id = ?", hogarId);
    }

    @Test
    void unGastoNuevoArrancaActivo() {
        UUID id = expenses.saveAndFlush(gasto("Kiosco", LocalDate.of(2026, 9, 13))).getId();
        entityManager.clear();

        Expense leido = expenses.findById(id).orElseThrow();
        assertThat(leido.isActive()).isTrue();
        assertThat(leido.getDeletedAt()).isNull();
    }

    @Test
    void borrarUnGastoLoMarcaYNoLoElimina() {
        UUID id = expenses.saveAndFlush(gasto("Kiosco", LocalDate.of(2026, 9, 13))).getId();

        expenses.deleteById(id);
        expenses.flush();
        entityManager.clear();

        // La fila sigue en la tabla, marcada y con la fecha de baja.
        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "select active, deleted_at, updated_at from expenses where id = ?", id);
        assertThat(fila.get("active")).isEqualTo(false);
        assertThat(fila.get("deleted_at")).isNotNull();
        assertThat(fila.get("updated_at")).isNotNull();
    }

    @Test
    void unGastoDadoDeBajaNoApareceEnNingunaConsulta() {
        Expense activo = expenses.saveAndFlush(gasto("Activo", LocalDate.of(2026, 9, 10)));
        Expense dadoDeBaja = expenses.saveAndFlush(gasto("Dado de baja", LocalDate.of(2026, 9, 11)));

        expenses.delete(dadoDeBaja);
        expenses.flush();
        entityManager.clear();

        assertThat(expenses.findById(dadoDeBaja.getId())).isEmpty();
        assertThat(expenses.findAll()).extracting(Expense::getMerchant).containsExactly("Activo");
        assertThat(expenses.findByExpenseDateBetweenOrderByExpenseDateDesc(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .extracting(Expense::getMerchant).containsExactly("Activo");
        // Los dos están sin categoría: la bandeja tampoco muestra el dado de baja.
        assertThat(expenses.findByCategoryIdIsNullOrderByExpenseDateDesc())
                .extracting(Expense::getMerchant).containsExactly("Activo");
        assertThat(expenses.findByOwnerId(duenoId)).extracting(Expense::getId)
                .containsExactly(activo.getId());
    }

    @Test
    void unaCategoriaDadaDeBajaNoSeOfreceNiOcupaSuNombre() {
        Category vieja = categories.saveAndFlush(new Category(hogarId, "Transporte"));
        categories.saveAndFlush(new Category(hogarId, "Comida"));

        categories.deleteById(vieja.getId());
        categories.flush();
        entityManager.clear();

        assertThat(categories.findByIdAndActiveTrue(vieja.getId())).isEmpty();
        assertThat(categories.findByNameIgnoreCaseAndActiveTrue("transporte")).isEmpty();
        assertThat(categories.findAllByActiveTrueOrderByNameAsc())
                .extracting(Category::getName).containsExactly("Comida");

        // El índice parcial de la V4 deja crear otra con el mismo nombre, y la búsqueda por
        // nombre encuentra solo la activa.
        Category nueva = categories.saveAndFlush(new Category(hogarId, "Transporte"));
        entityManager.clear();
        assertThat(categories.findByNameIgnoreCaseAndActiveTrue("transporte"))
                .get().extracting(Category::getId).isEqualTo(nueva.getId());
    }

    @Test
    void borrarUnaCategoriaLaMarcaYNoLaElimina() {
        UUID id = categories.saveAndFlush(new Category(hogarId, "Salud")).getId();

        categories.deleteById(id);
        categories.flush();
        entityManager.clear();

        // Category no tiene @SQLRestriction: findById la sigue viendo, marcada.
        Category leida = categories.findById(id).orElseThrow();
        assertThat(leida.isActive()).isFalse();
        assertThat(leida.getDeletedAt()).isNotNull();
    }

    @Test
    void elHistorialYElAnalisisConservanLaCategoriaDadaDeBaja() {
        Category salud = categories.saveAndFlush(new Category(hogarId, "Salud"));
        Expense gasto = gasto("Farmacity", LocalDate.of(2026, 8, 13));
        gasto.setCategoryId(salud.getId());
        UUID gastoId = expenses.saveAndFlush(gasto).getId();

        categories.deleteById(salud.getId());
        categories.flush();
        entityManager.clear();

        assertThat(expenses.findById(gastoId).orElseThrow().getCategoryId())
                .isEqualTo(salud.getId());
        assertThat(categories.findAllById(List.of(salud.getId())))
                .extracting(Category::getName).containsExactly("Salud");

        // El caso de la review de la PR #17: una consulta de análisis que une gastos con
        // categorías. Con @SQLRestriction en Category, Hibernate agregaba "c.active" a la
        // condición del join y el gasto de agosto salía sin categoría (left join) o
        // desaparecía del total (join).
        List<Object[]> totales = entityManager.createQuery(
                        "select c.name, sum(e.amount) from Expense e"
                                + " join Category c on c.id = e.categoryId"
                                + " where e.expenseDate between :desde and :hasta"
                                + " group by c.name", Object[].class)
                .setParameter("desde", LocalDate.of(2026, 8, 1))
                .setParameter("hasta", LocalDate.of(2026, 8, 31))
                .getResultList();
        assertThat(totales).singleElement().satisfies(fila -> {
            assertThat(fila[0]).isEqualTo("Salud");
            assertThat((BigDecimal) fila[1]).isEqualByComparingTo("100.00");
        });
    }

    @Test
    void darDeBajaUnaCategoriaEliminaSusReglasYLiberaSusPatrones() {
        Category supermercado = categories.saveAndFlush(new Category(hogarId, "Supermercado"));
        UUID regla = rules.saveAndFlush(
                new CategoryRule(hogarId, "coto", supermercado.getId())).getId();

        categories.deleteById(supermercado.getId());
        categories.flush();
        entityManager.clear();

        // El trigger de la V4 eliminó la regla: el motor ya no puede asignar la categoría dada
        // de baja.
        assertThat(rules.findById(regla)).isEmpty();
        assertThat(rules.findByPatternIgnoreCase("coto")).isEmpty();

        // Y el patrón queda libre para otra categoría.
        Category almacen = categories.saveAndFlush(new Category(hogarId, "Almacén"));
        rules.saveAndFlush(new CategoryRule(hogarId, "coto", almacen.getId()));
        entityManager.clear();
        assertThat(rules.findByPatternIgnoreCase("coto"))
                .get().extracting(CategoryRule::getCategoryId).isEqualTo(almacen.getId());
    }

    private Expense gasto(String comercio, LocalDate fecha) {
        return new Expense(hogarId, duenoId, comercio, new BigDecimal("100.00"), fecha,
                PaymentMethod.EFECTIVO);
    }
}
