package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.CategoryRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bajo RLS: ver la nota de {@link CategoryRepository} sobre por qué no se pasa el hogar y por qué
 * la interfaz es {@code @Transactional(readOnly = true)}.
 */
@Transactional(readOnly = true)
public interface CategoryRuleRepository extends JpaRepository<CategoryRule, UUID> {

    /** Case-insensitive, en línea con {@code category_rules_household_pattern_key}. */
    Optional<CategoryRule> findByPatternIgnoreCase(String pattern);

    List<CategoryRule> findAllByOrderByPatternAsc();

    List<CategoryRule> findByCategoryId(UUID categoryId);

    /**
     * Las reglas que aplican: las de categorías activas (RN-16), ordenadas por patrón. Es lo que usa el
     * motor y lo que ve el usuario.
     *
     * <p>Al dar de baja una categoría, el trigger de la V4 borra sus reglas, así que no debería quedar
     * ninguna de una categoría inactiva. El join igual las descarta: el motor no depende de que nadie
     * haya escrito en la tabla por fuera de la aplicación.
     */
    @Query("""
            select r from CategoryRule r join Category c on c.id = r.categoryId
            where c.active
            order by r.pattern""")
    List<CategoryRule> findAllOfActiveCategories();
}
