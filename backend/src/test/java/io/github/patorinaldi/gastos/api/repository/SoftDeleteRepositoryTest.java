package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.IntegrationTest;
import io.github.patorinaldi.gastos.api.domain.Category;
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
 * borrarla, y las filas dadas de baja no aparecen en ninguna consulta por JPA.
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
                duenoId, hogarId, "baja@example.test", "hash", "Dueño");
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
    void unaCategoriaDadaDeBajaNoApareceNiOcupaSuNombre() {
        Category vieja = categories.saveAndFlush(new Category(hogarId, "Transporte"));
        categories.saveAndFlush(new Category(hogarId, "Comida"));

        categories.deleteById(vieja.getId());
        categories.flush();
        entityManager.clear();

        assertThat(categories.findById(vieja.getId())).isEmpty();
        assertThat(categories.findByNameIgnoreCase("transporte")).isEmpty();
        assertThat(categories.findAllByOrderByNameAsc())
                .extracting(Category::getName).containsExactly("Comida");

        // El índice parcial de la V4 deja crear otra con el mismo nombre, y la búsqueda por
        // nombre encuentra solo la activa.
        Category nueva = categories.saveAndFlush(new Category(hogarId, "Transporte"));
        entityManager.clear();
        assertThat(categories.findByNameIgnoreCase("transporte"))
                .get().extracting(Category::getId).isEqualTo(nueva.getId());
    }

    @Test
    void elHistorialConservaLaCategoriaDadaDeBaja() {
        Category salud = categories.saveAndFlush(new Category(hogarId, "Salud"));
        Expense gasto = gasto("Farmacity", LocalDate.of(2026, 9, 13));
        gasto.setCategoryId(salud.getId());
        UUID gastoId = expenses.saveAndFlush(gasto).getId();

        categories.deleteById(salud.getId());
        categories.flush();
        entityManager.clear();

        // El gasto sigue activo y apunta a la categoría dada de baja.
        assertThat(expenses.findById(gastoId).orElseThrow().getCategoryId())
                .isEqualTo(salud.getId());

        // Para mostrarla en el historial, la consulta que incluye las inactivas la encuentra.
        List<Category> historicas = categories.findAllByIdIncludingInactive(List.of(salud.getId()));
        assertThat(historicas).singleElement().satisfies(categoria -> {
            assertThat(categoria.getName()).isEqualTo("Salud");
            assertThat(categoria.isActive()).isFalse();
            assertThat(categoria.getDeletedAt()).isNotNull();
        });
    }

    private Expense gasto(String comercio, LocalDate fecha) {
        return new Expense(hogarId, duenoId, comercio, new BigDecimal("100.00"), fecha,
                PaymentMethod.EFECTIVO);
    }
}
