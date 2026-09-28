package io.github.patorinaldi.gastos.api.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Contratos de M5, análisis (RF-25 a RF-29).
 *
 * <p>Los totales viajan como cadena decimal por el mismo motivo que los importes de un gasto
 * (RD-01): son sumas de valores exactos y no pueden pasar por un punto flotante en el camino.
 */
public final class AnalysisContracts {

    private AnalysisContracts() {
    }

    public record SummaryResponse(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
            List<CategoryTotal> byCategory,
            List<OwnerTotal> byOwner,
            List<PaymentMethodTotal> byPaymentMethod) {
    }

    /** {@code categoryId} y {@code name} nulos son el total de los gastos sin categoría (RN-12). */
    public record CategoryTotal(
            UUID categoryId,
            String name,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total) {
    }

    /** Incluye a quien se cambió de hogar: sus gastos quedaron en este (RN-18). */
    public record OwnerTotal(
            UUID ownerId,
            String name,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total) {
    }

    public record PaymentMethodTotal(
            PaymentMethodValue paymentMethod,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total) {
    }

    /**
     * Serie mensual y su media histórica. La media se calcula solo sobre los meses con gasto
     * registrado (RN-14): los meses anteriores al primer gasto no cuentan como cero, porque eso
     * hundiría la media de un hogar que empezó a usar el sistema hace poco.
     */
    public record MonthlySeriesResponse(
            List<MonthlyPoint> months,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal average) {
    }

    /** El mes viaja como {@code "2026-09"}. */
    public record MonthlyPoint(
            YearMonth month,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total) {
    }

    public record MerchantTotal(
            String merchant,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
            int expenses) {
    }
}
