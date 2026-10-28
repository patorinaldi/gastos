package io.github.patorinaldi.gastos.api.service.analysis;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Total del período y sus desgloses (RF-25, RF-26). Cada lista va de mayor a menor total. */
public record Summary(
        BigDecimal total,
        List<CategoryAmount> byCategory,
        List<OwnerAmount> byOwner,
        List<PaymentMethodAmount> byPaymentMethod) {

    /** {@code categoryId} y {@code name} nulos: los gastos sin categoría (RN-12). */
    public record CategoryAmount(UUID categoryId, String name, BigDecimal total) {
    }

    public record OwnerAmount(UUID ownerId, String name, BigDecimal total) {
    }

    public record PaymentMethodAmount(PaymentMethod paymentMethod, BigDecimal total) {
    }
}
