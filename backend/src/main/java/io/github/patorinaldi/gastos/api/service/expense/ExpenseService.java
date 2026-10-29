package io.github.patorinaldi.gastos.api.service.expense;

import io.github.patorinaldi.gastos.api.domain.Category;
import io.github.patorinaldi.gastos.api.domain.Expense;
import io.github.patorinaldi.gastos.api.domain.User;
import io.github.patorinaldi.gastos.api.repository.CategoryRepository;
import io.github.patorinaldi.gastos.api.repository.CategoryRuleRepository;
import io.github.patorinaldi.gastos.api.repository.ExpenseRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.category.CategoryUnavailableException;
import io.github.patorinaldi.gastos.api.service.category.RuleSet;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M3, gastos (RF-13 a RF-18): alta, consulta, edición, baja lógica y listado con filtros.
 *
 * <p>Ningún método recibe ni consulta el hogar: {@code expenses} y {@code categories} están bajo RLS,
 * cada método es transaccional y {@code HouseholdTransactionListener} fija el hogar de la sesión al
 * empezar. Un gasto o una categoría de otro hogar no se encuentran, así que responden igual que uno
 * inexistente. {@code users} no tiene RLS, pero solo se lee por el responsable de gastos que ya
 * pasaron ese filtro, como en el análisis.
 *
 * <p>Los gastos dados de baja no aparecen en ninguna lectura (RN-16, ver {@link Expense}).
 *
 * <p>Los registros del log llevan solo identificadores: ni importes ni comercios.
 */
@Service
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    private static final int MONEY_SCALE = 2;

    /**
     * Por fecha descendente (RF-17). La fecha no alcanza para un orden estable, porque muchos gastos
     * comparten día: sin desempate, un gasto podría aparecer en dos páginas o en ninguna.
     */
    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("expenseDate"), Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ExpenseRepository expenses;
    private final CategoryRepository categories;
    private final CategoryRuleRepository rules;
    private final UserRepository users;
    private final Clock clock;
    private final ZoneId zone;

    public ExpenseService(ExpenseRepository expenses,
                          CategoryRepository categories,
                          CategoryRuleRepository rules,
                          UserRepository users,
                          Clock clock,
                          ZoneId zone) {
        this.expenses = expenses;
        this.categories = categories;
        this.rules = rules;
        this.users = users;
        this.clock = clock;
        this.zone = zone;
    }

    /**
     * RF-13, RF-14. Registra un gasto a nombre del usuario, en su hogar. Sin fecha, la de hoy en la
     * zona de {@code TimeZoneConfiguration}, la misma con la que el análisis decide el mes en curso.
     *
     * <p>Sin categoría, la resuelve el motor de M4 con las reglas del hogar (RF-20, ver {@link RuleSet}).
     * Si ninguna coincide, el gasto queda en la bandeja (RF-21, RN-12). Una categoría elegida a mano
     * manda sobre las reglas.
     *
     * @throws CategoryUnavailableException si la categoría indicada no existe o está dada de baja
     */
    @Transactional
    public ExpenseView create(AuthenticatedUser user, NewExpense expense) {
        String merchant = expense.merchant().strip();
        UUID categoryId;
        if (expense.categoryId() != null) {
            requireAssignable(expense.categoryId());
            categoryId = expense.categoryId();
        } else {
            categoryId = RuleSet.of(rules.findAllOfActiveCategories()).categoryFor(merchant).orElse(null);
        }
        LocalDate date = expense.expenseDate() != null ? expense.expenseDate() : today();
        Expense created = new Expense(user.householdId(), user.userId(), merchant,
                money(expense.amount()), date, expense.paymentMethod());
        created.setCategoryId(categoryId);
        // saveAndFlush: las fechas de creación y modificación se completan al insertar, y van en la respuesta.
        Expense saved = expenses.saveAndFlush(created);
        log.info("Gasto {} registrado por el usuario {} en el hogar {}", saved.getId(), user.userId(), user.householdId());
        return view(saved);
    }

    /** @throws ExpenseNotFoundException si no existe, está dado de baja o es de otro hogar */
    @Transactional(readOnly = true)
    public ExpenseView get(UUID id) {
        return view(expenses.findById(id).orElseThrow(ExpenseNotFoundException::new));
    }

    /**
     * RF-15. Modifica los campos que vienen; el resto queda como estaba. Cualquier integrante puede
     * editar cualquier gasto del hogar (HU-09), incluso uno de alguien que ya se fue (RN-18). El
     * responsable no se edita: es quien cargó el gasto.
     *
     * <p>Una categoría nueva tiene que estar activa (RN-16). Si la que viene es la que el gasto ya
     * tiene, no se vuelve a validar: un cliente que reenvía el formulario completo tiene que poder
     * corregir el importe de un gasto viejo aunque su categoría se haya dado de baja después.
     *
     * <p>El gasto se bloquea al leerlo (ver {@link ExpenseRepository#lockById}).
     *
     * @throws ExpenseNotFoundException     si no existe, está dado de baja o es de otro hogar
     * @throws CategoryUnavailableException si la categoría nueva no existe o está dada de baja
     */
    @Transactional
    public ExpenseView update(AuthenticatedUser user, UUID id, ExpenseChanges changes) {
        Expense expense = expenses.lockById(id).orElseThrow(ExpenseNotFoundException::new);
        if (changes.categoryId() != null && !changes.categoryId().equals(expense.getCategoryId())) {
            requireAssignable(changes.categoryId());
        }

        if (changes.amount() != null) {
            expense.setAmount(money(changes.amount()));
        }
        if (changes.merchant() != null) {
            expense.setMerchant(changes.merchant().strip());
        }
        if (changes.expenseDate() != null) {
            expense.setExpenseDate(changes.expenseDate());
        }
        if (changes.paymentMethod() != null) {
            expense.setPaymentMethod(changes.paymentMethod());
        }
        if (changes.uncategorized()) {
            expense.setCategoryId(null);
        } else if (changes.categoryId() != null) {
            expense.setCategoryId(changes.categoryId());
        }

        // La fecha de modificación se completa al escribir, y va en la respuesta.
        expenses.flush();
        log.info("Gasto {} modificado por el usuario {}", id, user.userId());
        return view(expense);
    }

    /**
     * RF-16, RN-16. Baja lógica: el gasto deja de aparecer en el listado, la bandeja y el análisis,
     * pero la fila se conserva. Dar de baja uno ya dado de baja responde como uno inexistente.
     *
     * @throws ExpenseNotFoundException si no existe, ya está dado de baja o es de otro hogar
     */
    @Transactional
    public void delete(AuthenticatedUser user, UUID id) {
        Expense expense = expenses.lockById(id).orElseThrow(ExpenseNotFoundException::new);
        expenses.delete(expense);
        log.info("Gasto {} dado de baja por el usuario {}", id, user.userId());
    }

    /**
     * RF-17, RF-18. Los gastos del hogar que cumplen todos los filtros, por fecha descendente y de a
     * una página. Sin período, el mes en curso (RD-07), igual que el resumen del análisis.
     *
     * <p>La bandeja (RF-22) es la excepción: sin período muestra todos los gastos sin categoría, de
     * cualquier mes. Es una lista de trabajo pendiente, no el gasto de un período, y con el mes en
     * curso por defecto un gasto del mes anterior quedaría sin clasificar y fuera de la vista.
     *
     * <p>Los nombres de categorías y responsables se buscan de una vez para toda la página, no gasto
     * por gasto. Las categorías dadas de baja también: sus gastos la conservan (RN-16).
     */
    @Transactional(readOnly = true)
    public Page<ExpenseView> list(ExpenseCriteria criteria, int page, int size) {
        YearMonth current = YearMonth.from(today());
        ExpenseCriteria filter = criteria.uncategorized()
                ? criteria
                : criteria.withDefaultPeriod(current.atDay(1), current.atEndOfMonth());
        Page<Expense> found = expenses.findAll(matching(filter), PageRequest.of(page, size, NEWEST_FIRST));
        return new PageImpl<>(views(found.getContent()), found.getPageable(), found.getTotalElements());
    }

    /**
     * Las condiciones del listado. Los valores van como parámetros de la consulta; la restricción de
     * los activos la agrega Hibernate por el {@code @SQLRestriction} de {@link Expense}.
     */
    private static Specification<Expense> matching(ExpenseCriteria filter) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (filter.from() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), filter.from()));
            }
            if (filter.to() != null) {
                where.add(cb.lessThanOrEqualTo(root.get("expenseDate"), filter.to()));
            }
            if (filter.uncategorized()) {
                where.add(cb.isNull(root.get("categoryId")));
            } else if (filter.categoryId() != null) {
                where.add(cb.equal(root.get("categoryId"), filter.categoryId()));
            }
            if (filter.ownerId() != null) {
                where.add(cb.equal(root.get("ownerId"), filter.ownerId()));
            }
            if (filter.paymentMethod() != null) {
                where.add(cb.equal(root.get("paymentMethod"), filter.paymentMethod()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /**
     * La categoría tiene que existir en el hogar y estar activa (RN-08, RN-16). Sin esta validación,
     * una de otro hogar llegaría al insert y la rechazaría la clave foránea compuesta con un 500.
     */
    private void requireAssignable(UUID categoryId) {
        if (categories.findByIdAndActiveTrue(categoryId).isEmpty()) {
            throw new CategoryUnavailableException();
        }
    }

    private ExpenseView view(Expense expense) {
        return views(List.of(expense)).getFirst();
    }

    private List<ExpenseView> views(List<Expense> page) {
        List<UUID> categoryIds = page.stream().map(Expense::getCategoryId).filter(Objects::nonNull).distinct().toList();
        List<UUID> ownerIds = page.stream().map(Expense::getOwnerId).distinct().toList();
        Map<UUID, String> categoryNames = categories.findAllById(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        Map<UUID, User> owners = users.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return page.stream()
                .map(e -> new ExpenseView(
                        e.getId(), e.getAmount(), e.getMerchant(), e.getExpenseDate(), e.getPaymentMethod(),
                        e.getCategoryId(), e.getCategoryId() == null ? null : categoryNames.get(e.getCategoryId()),
                        e.getOwnerId(), owners.get(e.getOwnerId()).getName(),
                        e.getCreatedAt(), e.getUpdatedAt()))
                .toList();
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    // El contrato admite "100" o "100.5"; se guardan y se responden como "100.00" y "100.50". Nunca
    // redondea: más de dos decimales ya los rechazó el contrato (RN-06).
    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY);
    }
}
