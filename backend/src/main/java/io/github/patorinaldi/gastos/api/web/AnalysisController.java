package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import io.github.patorinaldi.gastos.api.service.analysis.AnalysisFilter;
import io.github.patorinaldi.gastos.api.service.analysis.AnalysisService;
import io.github.patorinaldi.gastos.api.service.analysis.MonthlySeries;
import io.github.patorinaldi.gastos.api.service.analysis.Summary;
import io.github.patorinaldi.gastos.api.web.dto.AnalysisContracts;
import io.github.patorinaldi.gastos.api.web.dto.AnalysisContracts.AnalysisFilterRequest;
import io.github.patorinaldi.gastos.api.web.dto.AnalysisContracts.CategoryTotal;
import io.github.patorinaldi.gastos.api.web.dto.AnalysisContracts.MerchantTotal;
import io.github.patorinaldi.gastos.api.web.dto.AnalysisContracts.MonthlyPoint;
import io.github.patorinaldi.gastos.api.web.dto.AnalysisContracts.MonthlySeriesResponse;
import io.github.patorinaldi.gastos.api.web.dto.AnalysisContracts.OwnerTotal;
import io.github.patorinaldi.gastos.api.web.dto.AnalysisContracts.PaymentMethodTotal;
import io.github.patorinaldi.gastos.api.web.dto.AnalysisContracts.SummaryResponse;
import io.github.patorinaldi.gastos.api.web.dto.PaymentMethodValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * M5, análisis (RF-25 a RF-29). Exige sesión, y el hogar es siempre el de quien consulta: lo fija
 * RLS, no un parámetro.
 *
 * <p>Traduce entre los contratos de la API y los tipos de {@link AnalysisService}, que no conoce
 * {@code web.dto} (RNF-22).
 */
@RestController
@RequestMapping("/api/expenses")
class AnalysisController {

    private final AnalysisService analysis;

    AnalysisController(AnalysisService analysis) {
        this.analysis = analysis;
    }

    @GetMapping("/summary")
    SummaryResponse summary(@Valid AnalysisFilterRequest filter) {
        Summary summary = analysis.summary(toFilter(filter));
        return new SummaryResponse(
                summary.total(),
                summary.byCategory().stream()
                        .map(c -> new CategoryTotal(c.categoryId(), c.name(), c.total()))
                        .toList(),
                summary.byOwner().stream()
                        .map(o -> new OwnerTotal(o.ownerId(), o.name(), o.total()))
                        .toList(),
                summary.byPaymentMethod().stream()
                        .map(p -> new PaymentMethodTotal(toValue(p.paymentMethod()), p.total()))
                        .toList());
    }

    @GetMapping("/monthly")
    MonthlySeriesResponse monthly(@Valid AnalysisFilterRequest filter) {
        MonthlySeries series = analysis.monthly(toFilter(filter));
        return new MonthlySeriesResponse(
                series.months().stream().map(m -> new MonthlyPoint(m.month(), m.total())).toList(),
                series.average());
    }

    @GetMapping("/merchants")
    List<MerchantTotal> merchants(
            @Valid AnalysisFilterRequest filter,
            @RequestParam(defaultValue = "" + AnalysisContracts.DEFAULT_MERCHANTS)
            @Min(1) @Max(AnalysisContracts.MAX_MERCHANTS) int limit) {
        return analysis.topMerchants(toFilter(filter), limit).stream()
                .map(m -> new MerchantTotal(m.merchant(), m.total(), m.expenses()))
                .toList();
    }

    private static AnalysisFilter toFilter(AnalysisFilterRequest request) {
        UUID categoryId = request.categoryId() == null || request.isUncategorized()
                ? null
                : UUID.fromString(request.categoryId());
        PaymentMethod paymentMethod = request.paymentMethod() == null ? null : toDomain(request.paymentMethod());
        return new AnalysisFilter(
                request.from(), request.to(), categoryId, request.isUncategorized(), request.ownerId(), paymentMethod);
    }

    // Los dos enumerados están separados a propósito (RNF-22). Un switch sin default no compila si a
    // uno le falta un caso; valueOf por nombre compilaría y fallaría en ejecución con un 500.
    private static PaymentMethodValue toValue(PaymentMethod method) {
        return switch (method) {
            case EFECTIVO -> PaymentMethodValue.EFECTIVO;
            case TARJETA -> PaymentMethodValue.TARJETA;
            case TRANSFERENCIA -> PaymentMethodValue.TRANSFERENCIA;
        };
    }

    private static PaymentMethod toDomain(PaymentMethodValue value) {
        return switch (value) {
            case EFECTIVO -> PaymentMethod.EFECTIVO;
            case TARJETA -> PaymentMethod.TARJETA;
            case TRANSFERENCIA -> PaymentMethod.TRANSFERENCIA;
        };
    }
}
