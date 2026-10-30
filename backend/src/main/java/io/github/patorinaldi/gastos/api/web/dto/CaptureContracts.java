package io.github.patorinaldi.gastos.api.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.patorinaldi.gastos.api.web.dto.validation.DisplayNames;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contratos de M6, captura desde clientes automatizados (RF-30 a RF-32).
 *
 * <p>Es el único punto de entrada donde el importe llega como texto sin normalizar. Quien lo invoca
 * suele haberlo extraído de una notificación bancaria y lo manda tal como lo leyó, en formato local
 * ({@code "1.234,56"}), anglosajón ({@code "1,234.56"}) o sin separadores. Por eso el campo es
 * {@code String} y no {@code BigDecimal}: si fuera decimal, "1.234,56" ni siquiera ligaría, y el
 * error sería de formato JSON en lugar del error explicativo que el cliente necesita.
 */
public final class CaptureContracts {

    /** El medio de pago de una captura que no lo indica. */
    public static final PaymentMethodValue DEFAULT_PAYMENT_METHOD = PaymentMethodValue.TARJETA;

    // Espacios de cualquier tipo, incluidos los saltos de línea, el espacio duro y los de ancho cero.
    private static final java.util.regex.Pattern SPACES =
            java.util.regex.Pattern.compile("[\\s\\u00A0\\u2007\\u202F\\u200B\\uFEFF]+");

    private CaptureContracts() {
    }

    /**
     * El comercio llega, como el importe, tal como se leyó de una notificación: con saltos de línea,
     * espacios duros o espacios repetidos. Se normaliza antes de validarlo, con el mismo criterio con
     * el que el análisis agrupa los comercios: una sola forma de espacio, sin espacios en los extremos.
     * Lo que queda se valida como el comercio de cualquier gasto.
     *
     * <p>El medio de pago es opcional y por defecto es tarjeta: la captura sale de notificaciones del
     * banco, que son de compras con tarjeta o de transferencias, no de pagos en efectivo.
     */
    public record CaptureRequest(
            @NotBlank @Size(max = 50) String amount,
            @NotBlank @Size(max = 200)
            @Pattern(regexp = DisplayNames.PATTERN, message = DisplayNames.MESSAGE) String merchant,
            PaymentMethodValue paymentMethod) {

        public CaptureRequest {
            merchant = merchant == null ? null : SPACES.matcher(merchant).replaceAll(" ").strip();
            paymentMethod = paymentMethod == null ? DEFAULT_PAYMENT_METHOD : paymentMethod;
        }
    }

    /**
     * Devuelve el importe ya normalizado para que el cliente pueda mostrarlo y verificar que se
     * interpretó como esperaba. Si no se pudo interpretar, no hay respuesta: se rechaza la captura
     * en lugar de registrar un importe adivinado.
     *
     * <p>La categoría va por su nombre y no por su identificador: el token de cliente máquina solo
     * habilita la captura, así que el cliente no podría pedir las categorías para traducirlo. Con el
     * nombre puede avisar en qué categoría quedó el gasto, y sin él, que quedó en la bandeja.
     */
    public record CaptureResponse(
            UUID id,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            String merchant,
            String categoryName,
            LocalDate expenseDate) {
    }
}
