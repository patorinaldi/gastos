package io.github.patorinaldi.gastos.api.service.expense;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Los filtros del listado de gastos (RF-18). Todos son opcionales y se combinan: un filtro nulo no
 * restringe nada. Son los mismos que aplica el análisis (RF-29), que tiene su propio tipo para no
 * depender de este módulo.
 *
 * @param from          primer día del período, inclusive
 * @param to            último día del período, inclusive
 * @param categoryId    solo los gastos de esa categoría
 * @param uncategorized solo los gastos sin categoría (RN-12); no se combina con {@code categoryId}
 * @param ownerId       solo los gastos de ese integrante
 * @param paymentMethod solo los gastos con ese medio de pago
 */
public record ExpenseCriteria(
        LocalDate from,
        LocalDate to,
        UUID categoryId,
        boolean uncategorized,
        UUID ownerId,
        PaymentMethod paymentMethod) {

    public ExpenseCriteria {
        if (uncategorized && categoryId != null) {
            throw new IllegalArgumentException("uncategorized y categoryId no se combinan");
        }
    }

    /** Si no trae período, el mismo filtro con el período indicado; si trae, el mismo filtro. */
    public ExpenseCriteria withDefaultPeriod(LocalDate defaultFrom, LocalDate defaultTo) {
        if (from != null || to != null) {
            return this;
        }
        return new ExpenseCriteria(defaultFrom, defaultTo, categoryId, uncategorized, ownerId, paymentMethod);
    }
}
