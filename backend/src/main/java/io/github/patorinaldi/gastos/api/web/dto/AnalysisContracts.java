package io.github.patorinaldi.gastos.api.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Contratos de M5, análisis (RF-25 a RF-29).
 *
 * <p>Los totales viajan como cadena decimal por el mismo motivo que los importes de un gasto
 * (RD-01): son sumas de valores exactos y no pueden pasar por un punto flotante en el camino.
 *
 * <p><strong>Un período sin gastos no devuelve nulos.</strong> Los totales son {@code "0.00"} y las
 * listas vienen vacías. Dos motivos: {@code sum()} en SQL devuelve null sobre cero filas y la media
 * sería una división por cero, así que el servicio tiene que resolver los dos casos antes de
 * responder; y del otro lado, un total nulo obligaría a cada pantalla a distinguir "sin datos" de
 * "cero" para no mostrar un hueco. La interfaz distingue el período vacío por las listas vacías, y
 * sobre eso muestra el estado vacío que pide RNF-16.
 */
public final class AnalysisContracts {

    /** Cuántos comercios devuelve el ranking si no se pide otra cantidad, y el máximo. */
    public static final int DEFAULT_MERCHANTS = 10;
    public static final int MAX_MERCHANTS = 50;

    private AnalysisContracts() {
    }

    /**
     * Los mismos filtros que el listado de gastos, sin la paginación (RF-29): las reglas comunes están
     * en {@link ExpenseFilter}. Viajan como parámetros de la URL y todos son opcionales. Sin período,
     * el resumen y el ranking toman el mes en curso (RD-07), y la serie mensual toda la historia. Una
     * categoría o un integrante de otro hogar no dan error: no hay gastos que coincidan, y el
     * resultado es el de un período vacío.
     */
    public record AnalysisFilterRequest(
            LocalDate from,
            LocalDate to,
            @Pattern(regexp = ExpenseContracts.UUID_OR_UNCATEGORIZED,
                    message = "debe ser un identificador de categoría o " + ExpenseContracts.UNCATEGORIZED)
            String categoryId,
            UUID ownerId,
            PaymentMethodValue paymentMethod) implements ExpenseFilter {
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
     *
     * <p>Un hogar sin ningún gasto devuelve {@code months} vacío y {@code average} en
     * {@code "0.00"}: con cero meses no hay media que calcular y no se divide por cero.
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
