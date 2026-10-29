package io.github.patorinaldi.gastos.api.service.expense;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un gasto tal como lo devuelve el servicio, con los nombres de su categoría y su responsable. El
 * controlador lo traduce a {@code ExpenseResponse} (RNF-22).
 *
 * <p>{@code categoryId} y {@code categoryName} son nulos en los gastos de la bandeja (RN-12). La
 * categoría puede estar dada de baja, y el responsable puede haberse ido del hogar (RN-16, RN-18).
 */
public record ExpenseView(
        UUID id,
        BigDecimal amount,
        String merchant,
        LocalDate expenseDate,
        PaymentMethod paymentMethod,
        UUID categoryId,
        String categoryName,
        UUID ownerId,
        String ownerName,
        Instant createdAt,
        Instant updatedAt) {
}
