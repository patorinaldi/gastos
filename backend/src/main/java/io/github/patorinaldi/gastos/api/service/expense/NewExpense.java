package io.github.patorinaldi.gastos.api.service.expense;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un gasto a registrar, con los datos ya validados. El hogar y el responsable no van acá: salen
 * siempre del usuario autenticado (RF-14).
 *
 * @param amount        mayor que cero, con hasta dos decimales (RN-06)
 * @param expenseDate   nula para la fecha de hoy (RF-14)
 * @param paymentMethod obligatorio: el contrato pone {@code Efectivo} si no viene
 * @param categoryId    nula para dejarlo sin categoría (RN-12)
 */
public record NewExpense(
        BigDecimal amount,
        String merchant,
        LocalDate expenseDate,
        PaymentMethod paymentMethod,
        UUID categoryId) {
}
