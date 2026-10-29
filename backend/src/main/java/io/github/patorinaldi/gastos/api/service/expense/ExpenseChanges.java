package io.github.patorinaldi.gastos.api.service.expense;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Modificación parcial de un gasto (RF-15): lo que viene nulo no se toca.
 *
 * <p>Una categoría nula significa "no cambiar", así que devolver el gasto a la bandeja necesita su
 * propio indicador, {@code uncategorized}.
 *
 * @param uncategorized le saca la categoría al gasto (RN-12); no se combina con {@code categoryId}
 */
public record ExpenseChanges(
        BigDecimal amount,
        String merchant,
        LocalDate expenseDate,
        PaymentMethod paymentMethod,
        UUID categoryId,
        boolean uncategorized) {

    public ExpenseChanges {
        if (uncategorized && categoryId != null) {
            throw new IllegalArgumentException("uncategorized y categoryId no se combinan");
        }
    }
}
