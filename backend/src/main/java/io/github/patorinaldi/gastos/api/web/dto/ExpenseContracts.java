package io.github.patorinaldi.gastos.api.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
 *
 * <p>Las validaciones de estos contratos son las mismas restricciones que la base ya hace cumplir.
 * Están repetidas acá a propósito: así un dato mal formado se rechaza con un 400 que explica qué
 * campo está mal, en lugar de llegar al insert y volver como un 500 por violación de integridad.
 */
public final class ExpenseContracts {

    /** Tamaño de página cuando el cliente no pide uno. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Valor de {@code categoryId} que pide la bandeja de no categorizados (RF-22). */
    public static final String UNCATEGORIZED = "uncategorized";

    private static final String UUID_OR_UNCATEGORIZED =
            "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|"
                    + UNCATEGORIZED + ")$";

    /** Ni vacío ni solo espacios, en línea con el check {@code expenses_merchant_not_blank}. */
    private static final String NOT_BLANK = ".*\\S.*";

    private ExpenseContracts() {
    }

    /**
     * El cuerpo mínimo es importe y comercio, que es lo que hace barato registrar un gasto (RF-14).
     * Lo demás es opcional: sin fecha se usa la del día, sin categoría la resuelve el motor de M4
     * (RN-12) y el responsable sale siempre del usuario autenticado, nunca del cuerpo.
     *
     * <p>El medio de pago también es opcional, pero la columna es obligatoria y no tiene valor por
     * defecto en la base, así que el que falta lo pone este contrato: {@code Efectivo}. Se resuelve
     * acá y no en el servicio para que ningún punto de entrada pueda olvidarlo y terminar en un
     * insert fallido. Es el único valor que el sistema inventa, y el usuario puede corregirlo
     * editando el gasto.
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

        public CreateExpenseRequest {
            paymentMethod = paymentMethod == null ? PaymentMethodValue.EFECTIVO : paymentMethod;
        }
    }

    /**
     * Modificación parcial: lo que viene en null no se toca. Por eso no lleva {@code NotNull} y sí
     * las validaciones de rango, que se evalúan solo sobre lo que sí viene.
     *
     * <p>El comercio lleva {@code Pattern} y no {@code NotBlank}: null significa "no cambiar" y es
     * válido, pero " " sí es un cambio, y es uno que la base rechaza.
     *
     * <p>Para sacarle la categoría a un gasto y devolverlo a la bandeja no alcanza con un null,
     * que acá significa "no cambiar": esa operación necesita su propio campo y queda para M3.
     */
    public record UpdateExpenseRequest(
            @Positive @Digits(integer = 10, fraction = 2)
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            @Pattern(regexp = NOT_BLANK, message = "no puede estar en blanco")
            @Size(max = 200) String merchant,
            LocalDate expenseDate,
            PaymentMethodValue paymentMethod,
            UUID categoryId) {
    }

    /**
     * Filtros del listado, que llegan como parámetros de consulta y se combinan entre sí (RF-17,
     * RF-18). Las mismas condiciones valen para el análisis (RF-29).
     *
     * <p>{@code from} y {@code to} nulos significan el mes en curso (RD-07). La paginación tiene
     * valores por defecto porque los parámetros que no vienen llegan nulos: sin ellos, un listado
     * pedido sin paginación quedaría en página 0 de tamaño 0 y la validación lo rechazaría con un
     * 400, cuando es la forma más común de pedirlo. El tope de 100 lo fija RNF-12: sin límite, una
     * sola petición podría pedir el historial completo del hogar.
     *
     * <p>{@code categoryId} es texto y no UUID porque admite además {@code uncategorized} (RF-22);
     * un identificador nulo ahí significaría "sin filtro", que es justo lo contrario. Al ser texto,
     * lo valida un patrón: sin él, cualquier cosa que no sea un UUID reventaría al convertirla y
     * devolvería un 500 en vez de un 400.
     */
    public record ExpenseFilterRequest(
            LocalDate from,
            LocalDate to,
            @Pattern(regexp = UUID_OR_UNCATEGORIZED,
                    message = "debe ser un identificador de categoría o " + UNCATEGORIZED)
            String categoryId,
            UUID ownerId,
            PaymentMethodValue paymentMethod,
            @PositiveOrZero Integer page,
            @Positive @Max(100) Integer size) {

        public ExpenseFilterRequest {
            page = page == null ? 0 : page;
            size = size == null ? DEFAULT_PAGE_SIZE : size;
        }

        public boolean isUncategorized() {
            return UNCATEGORIZED.equals(categoryId);
        }
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
