package io.github.patorinaldi.gastos.api.service.category;

import io.github.patorinaldi.gastos.api.domain.Category;
import io.github.patorinaldi.gastos.api.domain.CategoryRule;
import io.github.patorinaldi.gastos.api.repository.CategoryRepository;
import io.github.patorinaldi.gastos.api.repository.CategoryRuleRepository;
import io.github.patorinaldi.gastos.api.repository.ExpenseRepository;
import io.github.patorinaldi.gastos.api.repository.ExpenseRepository.UncategorizedExpense;
import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M4, reglas patrón → categoría (RF-23, RF-43): listado, alta con reclasificación retroactiva y
 * eliminación. Cómo coincide un patrón con un comercio lo define {@link Patterns}, y cuál gana entre
 * varias, {@link RuleSet}.
 *
 * <p>{@code category_rules}, {@code categories} y {@code expenses} están bajo RLS: como en
 * {@link CategoryService}, ningún método nombra el hogar.
 */
@Service
public class CategoryRuleService {

    private static final Logger log = LoggerFactory.getLogger(CategoryRuleService.class);

    static final String PATTERN_INDEX = "category_rules_household_pattern_key";

    private final CategoryRuleRepository rules;
    private final CategoryRepository categories;
    private final ExpenseRepository expenses;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public CategoryRuleService(CategoryRuleRepository rules,
                               CategoryRepository categories,
                               ExpenseRepository expenses,
                               Clock clock,
                               PlatformTransactionManager transactionManager) {
        this.rules = rules;
        this.categories = categories;
        this.expenses = expenses;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Las reglas que aplican, las de categorías activas, por patrón. */
    @Transactional(readOnly = true)
    public List<RuleView> list() {
        List<CategoryRule> found = rules.findAllOfActiveCategories();
        Map<UUID, String> names = categories.findAllById(found.stream().map(CategoryRule::getCategoryId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));
        return found.stream()
                .map(r -> new RuleView(r.getId(), r.getPattern(), r.getCategoryId(), names.get(r.getCategoryId())))
                .toList();
    }

    /**
     * Crea una regla y la aplica a la bandeja (RF-23, CU-05). El patrón se guarda normalizado (ver
     * {@link Patterns}), así que la interfaz puede proponer el comercio tal como está y el servidor lo
     * lleva a la forma de un patrón.
     *
     * <p>La categoría se bloquea mientras se crea la regla (ver {@link CategoryRepository#lockActiveById}).
     * La comprobación de patrón repetido compara claves y dice a qué categoría apunta la regla existente.
     * Dos pedidos simultáneos con el mismo patrón la pasan los dos, y ahí decide el índice
     * {@value #PATTERN_INDEX}, como en {@link CategoryService}.
     *
     * <p>Un gasto que se carga en el mismo instante en que se crea la regla puede quedar en la bandeja:
     * el alta no ve la regla todavía sin confirmar, y la reclasificación no ve el gasto. Queda a la vista
     * en la bandeja, y se clasifica a mano.
     *
     * @throws InvalidPatternException       si el patrón normalizado tiene menos de tres letras o números,
     *                                       o más de 200 caracteres
     * @throws CategoryUnavailableException  si la categoría no existe, está dada de baja o es de otro hogar
     * @throws PatternTakenException         si ya hay una regla con el mismo patrón (RN-10)
     */
    public CreatedRule create(AuthenticatedUser user, String rawPattern, UUID categoryId) {
        String pattern = Patterns.normalize(rawPattern);
        if (Patterns.matchKey(pattern).length() < Patterns.MIN_KEY_LENGTH) {
            throw InvalidPatternException.tooShort();
        }
        if (pattern.length() > Patterns.MAX_LENGTH) {
            throw InvalidPatternException.tooLong();
        }
        try {
            return transactions.execute(status -> createLocked(user, pattern, categoryId));
        } catch (DataIntegrityViolationException ex) {
            if (UniqueViolations.of(ex, PATTERN_INDEX)) {
                throw new PatternTakenException(null);
            }
            throw ex;
        }
    }

    private CreatedRule createLocked(AuthenticatedUser user, String pattern, UUID categoryId) {
        Category category = categories.lockActiveById(categoryId).orElseThrow(CategoryUnavailableException::new);
        String key = Patterns.matchKey(pattern);
        List<CategoryRule> current = new ArrayList<>(rules.findAllOfActiveCategories());
        for (CategoryRule existing : current) {
            if (Patterns.matchKey(existing.getPattern()).equals(key)) {
                throw new PatternTakenException(categories.findById(existing.getCategoryId())
                        .map(Category::getName).orElse(null));
            }
        }

        // saveAndFlush: la fecha de alta participa del desempate de RN-17 en la reclasificación.
        CategoryRule rule = rules.saveAndFlush(new CategoryRule(user.householdId(), pattern, category.getId()));
        current.add(rule);
        int reclassified = reclassify(RuleSet.of(current), key);
        log.info("Regla {} creada por el usuario {} en el hogar {}: {} gastos salieron de la bandeja",
                rule.getId(), user.userId(), user.householdId(), reclassified);
        return new CreatedRule(rule.getId(), rule.getPattern(), rule.getCategoryId(), reclassified);
    }

    /**
     * Reclasificación retroactiva (RN-13): los gastos activos de la bandeja que coinciden con el patrón
     * nuevo. Los que ya tienen categoría no se tocan, aunque coincidan: puede ser una corrección manual.
     *
     * <p>Cada gasto recibe la categoría que le daría el motor completo, con la regla nueva incluida, y
     * no necesariamente la de la regla nueva: con {@code uber eats} ya existente, crear {@code uber} no
     * manda a Transporte un "Uber Eats" que estaba en la bandeja (RN-17). Es lo que habría pasado si la
     * regla hubiera existido al cargarlo.
     *
     * @return cuántos gastos salieron de la bandeja
     */
    private int reclassify(RuleSet ruleSet, String patternKey) {
        Map<UUID, List<UUID>> byCategory = new HashMap<>();
        for (UncategorizedExpense expense : expenses.findUncategorized()) {
            if (MerchantKey.of(expense.getMerchant()).matches(patternKey)) {
                ruleSet.categoryFor(expense.getMerchant())
                        .ifPresent(c -> byCategory.computeIfAbsent(c, k -> new ArrayList<>()).add(expense.getId()));
            }
        }
        Instant now = clock.instant();
        int reclassified = 0;
        for (Map.Entry<UUID, List<UUID>> group : byCategory.entrySet()) {
            reclassified += expenses.assignCategoryIfUncategorized(group.getValue(), group.getKey(), now);
        }
        return reclassified;
    }

    /**
     * Elimina una regla. Es un borrado físico: una regla es una preferencia de clasificación, no un
     * hecho económico (RN-16). Los gastos que ya clasificó conservan su categoría (RF-43).
     *
     * @throws CategoryRuleNotFoundException si no existe o es de otro hogar
     */
    @Transactional
    public void delete(AuthenticatedUser user, UUID id) {
        CategoryRule rule = rules.findById(id).orElseThrow(CategoryRuleNotFoundException::new);
        rules.delete(rule);
        log.info("Regla {} eliminada por el usuario {}", id, user.userId());
    }
}
