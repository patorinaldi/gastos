package io.github.patorinaldi.gastos.api.service.analysis;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Los filtros del listado de gastos, aplicados al análisis (RF-29). Todos son opcionales y se
 * combinan: un filtro nulo no restringe nada.
 *
 * @param from          primer día del período, inclusive
 * @param to            último día del período, inclusive
 * @param categoryId    solo los gastos de esa categoría
 * @param uncategorized solo los gastos sin categoría (RN-12); no se combina con {@code categoryId}
 * @param ownerId       solo los gastos de ese integrante
 * @param paymentMethod solo los gastos con ese medio de pago
 */
public record AnalysisFilter(
        LocalDate from,
        LocalDate to,
        UUID categoryId,
        boolean uncategorized,
        UUID ownerId,
        PaymentMethod paymentMethod) {

    public AnalysisFilter {
        if (uncategorized && categoryId != null) {
            throw new IllegalArgumentException("uncategorized y categoryId no se combinan");
        }
    }

    /** Sin ningún filtro: toda la historia del hogar. */
    public static AnalysisFilter none() {
        return new AnalysisFilter(null, null, null, false, null, null);
    }

    /** Si no trae período, el mismo filtro con el período indicado; si trae, el mismo filtro. */
    public AnalysisFilter withDefaultPeriod(LocalDate defaultFrom, LocalDate defaultTo) {
        if (from != null || to != null) {
            return this;
        }
        return new AnalysisFilter(defaultFrom, defaultTo, categoryId, uncategorized, ownerId, paymentMethod);
    }

    /** El mismo filtro sin período: la media histórica no depende del período que se mira. */
    public AnalysisFilter withoutPeriod() {
        return new AnalysisFilter(null, null, categoryId, uncategorized, ownerId, paymentMethod);
    }
}
