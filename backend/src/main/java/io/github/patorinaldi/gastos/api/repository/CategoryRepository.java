package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.Category;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * categories está bajo RLS, así que ninguna firma recibe el hogar: el filtro lo aplica
 * Postgres con la política de la V2 sobre {@code app.current_household}.
 *
 * <p>Sin contexto de hogar en la transacción estas consultas devuelven cero filas, no todas.
 *
 * <p>Las categorías dadas de baja quedan fuera de todas las consultas y {@code delete} es una baja
 * lógica (ver {@link Category}). La única excepción es {@link #findAllByIdIncludingInactive}.
 */
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    /** Case-insensitive, en línea con el índice único {@code categories_household_name_key}. */
    Optional<Category> findByNameIgnoreCase(String name);

    List<Category> findAllByOrderByNameAsc();

    /**
     * Incluye las categorías dadas de baja, para mostrar la categoría de gastos históricos (RN-16).
     * Es nativa porque {@code @SQLRestriction} no alcanza a las consultas nativas; RLS sí, así que
     * sigue limitada al hogar activo. No usar para ofrecer categorías en nuevas asignaciones.
     */
    @Query(value = "select * from categories where id in (:ids)", nativeQuery = true)
    List<Category> findAllByIdIncludingInactive(Collection<UUID> ids);
}
