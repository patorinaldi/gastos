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
 *
 * <p>{@code delete} es una baja lógica, pero las categorías dadas de baja <em>no</em> se filtran
 * en todas las consultas (ver {@link Category}). {@code findById}, {@code findAllById} y
 * {@code findAll} las incluyen, que es lo que necesitan el historial y el análisis. Las consultas
 * con {@code ActiveTrue} son las que se usan para ofrecer categorías: el catálogo, la búsqueda por
 * nombre y la validación de una asignación nueva.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /**
     * Case-insensitive y solo entre las activas, en línea con el índice único parcial
     * {@code categories_household_name_key}.
     */
    Optional<Category> findByNameIgnoreCaseAndActiveTrue(String name);

    /** El catálogo que se ofrece al usuario. */
    List<Category> findAllByActiveTrueOrderByNameAsc();

    /** Para validar que la categoría de una asignación nueva (gasto o regla) siga activa. */
    Optional<Category> findByIdAndActiveTrue(UUID id);
}
