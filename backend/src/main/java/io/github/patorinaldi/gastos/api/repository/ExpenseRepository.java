package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.Expense;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bajo RLS: ver la nota de {@link CategoryRepository} sobre por qué no se pasa el hogar y por qué
 * la interfaz es {@code @Transactional(readOnly = true)}.
 *
 * <p>Los gastos dados de baja quedan fuera de todas las consultas y {@code delete} es una baja
 * lógica (ver {@link Expense}). Las consultas por {@link JpaSpecificationExecutor}, que arma el
 * listado con filtros, también los dejan fuera.
 */
@Transactional(readOnly = true)
public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    List<Expense> findByExpenseDateBetweenOrderByExpenseDateDesc(LocalDate from, LocalDate to);

    /** Bandeja de no categorizados: los gastos que ningún patrón resolvió en el alta. */
    List<Expense> findByCategoryIdIsNullOrderByExpenseDateDesc();

    List<Expense> findByOwnerId(UUID ownerId);

    /**
     * Lee y bloquea un gasto activo hasta el fin de la transacción, para editarlo o darlo de baja.
     * Dos ediciones del mismo gasto se serializan, y una edición que espera a una baja ya no lo
     * encuentra: Postgres vuelve a evaluar el {@code where} al obtener el bloqueo.
     *
     * <p>Es nativa, así que no la alcanza el {@code @SQLRestriction} de {@link Expense}: el filtro
     * de los activos va escrito. {@code for no key update} por el mismo motivo que
     * {@link UserRepository#lockById}.
     */
    @Query(value = "select * from expenses where id = ?1 and active for no key update", nativeQuery = true)
    Optional<Expense> lockById(UUID id);
}
