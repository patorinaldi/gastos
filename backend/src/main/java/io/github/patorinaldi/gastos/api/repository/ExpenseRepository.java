package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.Expense;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Bajo RLS: ver la nota de {@link CategoryRepository} sobre por qué no se pasa el hogar.
 */
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findByExpenseDateBetweenOrderByExpenseDateDesc(LocalDate from, LocalDate to);

    /** Bandeja de no categorizados: los gastos que ningún patrón resolvió en el alta. */
    List<Expense> findByCategoryIdIsNullOrderByExpenseDateDesc();

    List<Expense> findByOwnerId(UUID ownerId);
}
