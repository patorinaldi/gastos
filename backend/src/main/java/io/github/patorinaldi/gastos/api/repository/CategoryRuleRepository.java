package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.CategoryRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Bajo RLS: ver la nota de {@link CategoryRepository} sobre por qué no se pasa el hogar.
 */
public interface CategoryRuleRepository extends JpaRepository<CategoryRule, UUID> {

    /** Case-insensitive, en línea con {@code category_rules_household_pattern_key}. */
    Optional<CategoryRule> findByPatternIgnoreCase(String pattern);

    List<CategoryRule> findAllByOrderByPatternAsc();

    List<CategoryRule> findByCategoryId(UUID categoryId);
}
