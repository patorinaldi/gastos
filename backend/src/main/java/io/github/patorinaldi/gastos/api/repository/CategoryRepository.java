package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * categories está bajo RLS, así que ninguna firma recibe el hogar: el filtro lo aplica
 * Postgres con la política de la V2 sobre {@code app.current_household}.
 *
 * <p>Sin contexto de hogar en la transacción estas consultas devuelven cero filas, no todas.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** Case-insensitive, en línea con el índice único {@code categories_household_name_key}. */
    Optional<Category> findByNameIgnoreCase(String name);

    List<Category> findAllByOrderByNameAsc();
}
