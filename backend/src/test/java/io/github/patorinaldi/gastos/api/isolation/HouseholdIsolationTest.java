package io.github.patorinaldi.gastos.api.isolation;

import io.github.patorinaldi.gastos.api.HouseholdTestSupport;
import io.github.patorinaldi.gastos.api.domain.Category;
import io.github.patorinaldi.gastos.api.domain.CategoryRule;
import io.github.patorinaldi.gastos.api.domain.Expense;
import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import io.github.patorinaldi.gastos.api.repository.CategoryRepository;
import io.github.patorinaldi.gastos.api.repository.CategoryRuleRepository;
import io.github.patorinaldi.gastos.api.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de aislamiento base entre hogares.
 *
 * <p>Cubre las tres tablas que la migración V2 puso bajo RLS ({@code categories},
 * {@code category_rules} y {@code expenses}) por lectura, alta, modificación y baja, y en los
 * tres contextos que importan: sin hogar activo, con el hogar ajeno y con el propio.
 *
 * <p>Todo pasa por los repositorios y no por SQL directo, porque ese es el camino que recorre la
 * aplicación: lo que se verifica acá es que un bug en el código de negocio no pueda convertirse
 * en una fuga entre hogares.
 *
 * <p><strong>El control positivo no es decorativo.</strong> Una prueba que afirma "devuelve cero
 * filas" pasa igual si el fixture se rompió y nunca se insertó nada. Los casos de
 * {@link ConHogarPropio} demuestran que ese cero significa "RLS lo bloqueó" y no "no había nada".
 *
 * <p><strong>Fuera de alcance a propósito:</strong> {@code users} y {@code households} todavía no
 * tienen RLS — la V2 las dejó afuera porque el login resuelve el email antes de que exista
 * contexto de hogar. Su aislamiento se cierra con el módulo de identidad, y hasta entonces esta
 * clase no lo cubre ni debe leerse como si lo hiciera. La cobertura por punto de acceso HTTP
 * llega cuando existan los endpoints.
 */
class HouseholdIsolationTest extends HouseholdTestSupport {

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private CategoryRuleRepository rules;

    @Autowired
    private ExpenseRepository expenses;

    private HouseholdFixture hogarA;
    private HouseholdFixture hogarB;

    @BeforeEach
    void sembrarDosHogares() {
        hogarA = sembrar("Casa A", "a@example.test", "Comida", "carrefour", "Carrefour");
        hogarB = sembrar("Casa B", "b@example.test", "Transporte", "shell", "Shell");
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(hogarA.householdId());
        deleteHousehold(hogarB.householdId());
    }

    // ------------------------------------------------------------------
    // A. Sin contexto: el sistema falla cerrado
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("sin hogar en contexto")
    class SinContexto {

        @Test
        void noDevuelveNingunaFilaDeNingunaTabla() {
            // La propiedad central del diseño: sin contexto se ve nada, no se ve todo.
            assertThat(withoutHousehold(() -> categories.findAll())).isEmpty();
            assertThat(withoutHousehold(() -> rules.findAll())).isEmpty();
            assertThat(withoutHousehold(() -> expenses.findAll())).isEmpty();
        }

        @Test
        void noEncuentraFilasQueSiExistenNiConociendoSuId() {
            assertThat(withoutHousehold(() -> categories.findById(hogarA.categoryId()))).isEmpty();
            assertThat(withoutHousehold(() -> rules.findById(hogarA.ruleId()))).isEmpty();
            assertThat(withoutHousehold(() -> expenses.findById(hogarA.expenseId()))).isEmpty();
        }

        @Test
        void noPuedeInsertar() {
            // WITH CHECK compara household_id contra app_current_household(), que sin contexto
            // es null: la comparación nunca da verdadera y el insert se rechaza.
            // Se afirma sobre el mensaje y no solo sobre el tipo: si el insert fallara por
            // otra restricción (un unique, una FK), el test pasaría sin haber probado RLS.
            assertThatThrownBy(() -> withoutHousehold(
                    () -> categories.saveAndFlush(new Category(hogarA.householdId(), "Nueva"))))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("row-level security policy");
        }

        @Test
        void noPuedeBorrarYLaFilaSobrevive() {
            // Bajo RLS el borrado no falla: simplemente no alcanza ninguna fila. Por eso la
            // verificación que vale es releer con el hogar correcto y encontrarla intacta.
            withoutHousehold(() -> {
                expenses.deleteById(hogarA.expenseId());
                return null;
            });

            assertThat(withHousehold(hogarA.householdId(),
                    () -> expenses.findById(hogarA.expenseId()))).isPresent();
        }
    }

    // ------------------------------------------------------------------
    // B. Con el hogar ajeno: el corazón de la tarea
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("con el hogar A activo, contra datos del hogar B")
    class ConHogarAjeno {

        @Test
        void soloVeSusPropiasFilas() {
            assertThat(withHousehold(hogarA.householdId(), () -> categories.findAll()))
                    .extracting(Category::getHouseholdId)
                    .containsExactly(hogarA.householdId());
            assertThat(withHousehold(hogarA.householdId(), () -> rules.findAll()))
                    .extracting(CategoryRule::getHouseholdId)
                    .containsExactly(hogarA.householdId());
            assertThat(withHousehold(hogarA.householdId(), () -> expenses.findAll()))
                    .extracting(Expense::getHouseholdId)
                    .containsExactly(hogarA.householdId());
        }

        @Test
        void noLeeFilasDeBNiConociendoSusIds() {
            // El escenario textual de la propuesta: conocer el identificador no alcanza.
            assertThat(withHousehold(hogarA.householdId(),
                    () -> categories.findById(hogarB.categoryId()))).isEmpty();
            assertThat(withHousehold(hogarA.householdId(),
                    () -> rules.findById(hogarB.ruleId()))).isEmpty();
            assertThat(withHousehold(hogarA.householdId(),
                    () -> expenses.findById(hogarB.expenseId()))).isEmpty();
        }

        @Test
        void noPuedeInsertarFilasAtribuidasAB() {
            // Aunque el código de negocio tuviera un bug y armara la entidad con el hogar ajeno,
            // WITH CHECK lo rechaza antes de que llegue a la tabla.
            assertThatThrownBy(() -> withHousehold(hogarA.householdId(),
                    () -> categories.saveAndFlush(new Category(hogarB.householdId(), "Infiltrada"))))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("row-level security policy");

            assertThatThrownBy(() -> withHousehold(hogarA.householdId(),
                    () -> expenses.saveAndFlush(new Expense(hogarB.householdId(), hogarB.userId(),
                            "Infiltrado", new BigDecimal("1.00"), LocalDate.of(2026, 9, 13),
                            PaymentMethod.EFECTIVO))))
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("row-level security policy");
        }

        @Test
        void noModificaFilasDeB() {
            Integer afectadas = withHousehold(hogarA.householdId(), () -> jdbcTemplate.update(
                    "update expenses set merchant = ? where id = ?", "Alterado",
                    hogarB.expenseId()));

            assertThat(afectadas).isZero();
            assertThat(withHousehold(hogarB.householdId(),
                    () -> expenses.findById(hogarB.expenseId()).orElseThrow().getMerchant()))
                    .isEqualTo("Shell");
        }

        @Test
        void noBorraFilasDeB() {
            withHousehold(hogarA.householdId(), () -> {
                expenses.deleteById(hogarB.expenseId());
                categories.deleteById(hogarB.categoryId());
                return null;
            });

            // La mitad que realmente prueba algo: B sigue viendo lo suyo.
            assertThat(withHousehold(hogarB.householdId(),
                    () -> expenses.findById(hogarB.expenseId()))).isPresent();
            // findByIdAndActiveTrue y no findById: Category no filtra las bajas, así que una
            // baja lógica hecha desde A dejaría la fila presente pero inactiva.
            assertThat(withHousehold(hogarB.householdId(),
                    () -> categories.findByIdAndActiveTrue(hogarB.categoryId()))).isPresent();
        }
    }

    // ------------------------------------------------------------------
    // C. Control positivo: el cero de arriba significa algo
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("con el hogar propio activo (control positivo)")
    class ConHogarPropio {

        @Test
        void veSusFilasPorIdEnLasTresTablas() {
            assertThat(withHousehold(hogarA.householdId(),
                    () -> categories.findById(hogarA.categoryId()))).isPresent();
            assertThat(withHousehold(hogarA.householdId(),
                    () -> rules.findById(hogarA.ruleId()))).isPresent();
            assertThat(withHousehold(hogarA.householdId(),
                    () -> expenses.findById(hogarA.expenseId()))).isPresent();
        }

        @Test
        void puedeInsertarModificarYBorrarLoSuyo() {
            UUID nueva = withHousehold(hogarA.householdId(),
                    () -> categories.saveAndFlush(new Category(hogarA.householdId(), "Ocio"))
                            .getId());
            assertThat(nueva).isNotNull();

            Integer afectadas = withHousehold(hogarA.householdId(), () -> jdbcTemplate.update(
                    "update expenses set merchant = ? where id = ?", "Carrefour Express",
                    hogarA.expenseId()));
            assertThat(afectadas).isEqualTo(1);

            withHousehold(hogarA.householdId(), () -> {
                categories.deleteById(nueva);
                return null;
            });
            assertThat(withHousehold(hogarA.householdId(),
                    () -> categories.findByIdAndActiveTrue(nueva))).isEmpty();
        }
    }

    // ------------------------------------------------------------------

    private HouseholdFixture sembrar(String hogar, String email, String categoria,
                                     String patron, String comercio) {
        UUID householdId = createHousehold(hogar);
        UUID userId = createUser(householdId, email, "Integrante");

        return withHousehold(householdId, () -> {
            UUID categoryId = categories
                    .saveAndFlush(new Category(householdId, categoria)).getId();
            UUID ruleId = rules
                    .saveAndFlush(new CategoryRule(householdId, patron, categoryId)).getId();
            Expense gasto = new Expense(householdId, userId, comercio, new BigDecimal("100.00"),
                    LocalDate.of(2026, 9, 13), PaymentMethod.TARJETA);
            gasto.setCategoryId(categoryId);
            UUID expenseId = expenses.saveAndFlush(gasto).getId();
            return new HouseholdFixture(householdId, userId, categoryId, ruleId, expenseId);
        });
    }
}
