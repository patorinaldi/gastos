package io.github.patorinaldi.gastos.api.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
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

    private CaptureContracts() {
    }

    public record CaptureRequest(
            @NotBlank @Size(max = 50) String amount,
            @NotBlank @Size(max = 200) String merchant) {
    }

    /**
     * Devuelve el importe ya normalizado para que el cliente pueda mostrarlo y verificar que se
     * interpretó como esperaba. Si no se pudo interpretar, no hay respuesta: se rechaza la captura
     * en lugar de registrar un importe adivinado.
     */
    public record CaptureResponse(
            UUID id,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
            String merchant,
            UUID categoryId,
            LocalDate expenseDate) {
    }
}
