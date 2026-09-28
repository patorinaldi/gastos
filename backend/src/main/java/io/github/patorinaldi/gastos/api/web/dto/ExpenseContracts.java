package io.github.patorinaldi.gastos.api.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contratos de M3, gastos (RF-13 a RF-18).
 *
 * <p>Los importes viajan como cadena decimal, nunca como número JSON (RD-01). Un número JSON lo
 * interpretan como punto flotante la mayoría de los clientes, incluido JavaScript, y ahí un importe
 * pierde centavos antes de que el servidor lo vea. Por eso los campos son {@code BigDecimal} con
 * {@code JsonFormat} de cadena: el servidor mantiene precisión exacta y el cliente recibe texto.
 */
public final class ExpenseContracts {

    private ExpenseContracts() {
    }

    /**
     * El cuerpo mínimo es importe y comercio, que es lo que hace barato registrar un gasto (RF-14).
     * Lo demás es opcional: sin fecha se usa la del día, sin categoría la resuelve el motor de M4
     * (RN-12) y el responsable sale siempre del usuario autenticado, nunca del cuerpo.
     *
     * <p>{@code Digits} con dos decimales rechaza "1234.567" en lugar de redondearlo: un importe
     * que no es el que se cargó no se adivina (RN-06).
     */
    public record CreateExpenseRequest(
            @NotNull @Positive @Digits(integer = 10, fraction = 2)
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            @NotBlank @Size(max = 200) String merchant,
            LocalDate expenseDate,
            PaymentMethodValue paymentMethod,
            UUID categoryId) {
    }

    /**
     * Modificación parcial: lo que viene en null no se toca. Por eso no lleva {@code NotNull} y sí
     * las validaciones de rango, que se evalúan solo sobre lo que sí viene.
     *
     * <p>Para sacarle la categoría a un gasto y devolverlo a la bandeja no alcanza con un null,
     * que acá significa "no cambiar": esa operación necesita su propio campo y queda para M3.
     */
    public record UpdateExpenseRequest(
            @Positive @Digits(integer = 10, fraction = 2)
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            @Size(max = 200) String merchant,
            LocalDate expenseDate,
            PaymentMethodValue paymentMethod,
            UUID categoryId) {
    }

    /**
     * Filtros del listado, que llegan como parámetros de consulta y se combinan entre sí (RF-17,
     * RF-18). Las mismas condiciones valen para el análisis (RF-29).
     *
     * <p>{@code from} y {@code to} nulos significan el mes en curso (RD-07). {@code categoryId} es
     * texto y no UUID porque admite además el valor {@code uncategorized}, que es como se pide la
     * bandeja de no categorizados (RF-22): un identificador nulo ahí significaría "sin filtro", que
     * es justo lo contrario.
     *
     * <p>El tope de 100 lo fija RNF-12: sin límite, una sola petición podría pedir el historial
     * completo del hogar y tumbar el tiempo de respuesta.
     */
    public record ExpenseFilterRequest(
            LocalDate from,
            LocalDate to,
            String categoryId,
            UUID ownerId,
            PaymentMethodValue paymentMethod,
            @PositiveOrZero int page,
            @Positive @Max(100) int size) {

        public static final String UNCATEGORIZED = "uncategorized";
    }

    /**
     * Incluye el nombre de la categoría y del responsable, además de sus identificadores, para que
     * el listado no tenga que pedirlos aparte.
     *
     * <p>{@code categoryId} y {@code categoryName} son nulos en los gastos de la bandeja (RN-12).
     * El responsable puede haberse cambiado de hogar después de cargar el gasto: el gasto queda
     * donde estaba y conserva su nombre (RN-18).
     */
    public record ExpenseResponse(
            UUID id,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            String merchant,
            LocalDate expenseDate,
            PaymentMethodValue paymentMethod,
            UUID categoryId,
            String categoryName,
            UUID ownerId,
            String ownerName,
            Instant createdAt,
            Instant updatedAt) {
    }
}
