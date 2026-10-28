package io.github.patorinaldi.gastos.api.web.dto;

import jakarta.validation.constraints.AssertTrue;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Los filtros de gastos que comparten el listado y el análisis (RF-29): un solo lugar para sus
 * reglas, así los dos no se separan. Cada contrato los declara como componentes de su record, con el
 * patrón de {@link ExpenseContracts#UUID_OR_UNCATEGORIZED} en {@code categoryId}.
 *
 * <p>Las fechas llegan en ISO ({@code 2026-09-01}) por {@code spring.mvc.format.date}.
 */
public interface ExpenseFilter {

    /** Primer día del período, inclusive. */
    LocalDate from();

    /** Último día del período, inclusive. */
    LocalDate to();

    /** Un identificador de categoría o {@link ExpenseContracts#UNCATEGORIZED}. */
    String categoryId();

    UUID ownerId();

    PaymentMethodValue paymentMethod();

    @AssertTrue(message = "from no puede ser posterior a to")
    default boolean isPeriodOrdered() {
        return from() == null || to() == null || !from().isAfter(to());
    }

    default boolean isUncategorized() {
        return ExpenseContracts.UNCATEGORIZED.equals(categoryId());
    }
}
