package io.github.patorinaldi.gastos.api.web.dto;

import io.github.patorinaldi.gastos.api.web.dto.AnalysisContracts.MonthlyPoint;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.CreateExpenseRequest;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.ExpenseResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica la forma del JSON que promete la documentación de la API, con la configuración real de
 * Jackson de la aplicación.
 *
 * <p>Lo que se prueba acá no es el mapeo campo a campo, que el compilador ya sostiene, sino las
 * tres decisiones del contrato que un cambio de configuración podría romper sin que nada más se
 * entere: los importes como cadena, los medios de pago en español y las fechas en ISO.
 */
@JsonTest
class ApiContractsJsonTest {

    @Autowired
    private ObjectMapper json;

    @Test
    void amountsTravelAsStringAndNeverAsANumber() {
        String body = json.writeValueAsString(expenseResponse(new BigDecimal("1234.56")));

        assertThat(body).contains("\"amount\":\"1234.56\"");
    }

    // Serializar 1234.50 como número daría 1234.5 y el cliente mostraría un importe distinto del
    // que se cargó.
    @Test
    void amountsKeepTheirTrailingZero() {
        String body = json.writeValueAsString(expenseResponse(new BigDecimal("1234.50")));

        assertThat(body).contains("\"amount\":\"1234.50\"");
    }

    @Test
    void amountsAreAcceptedAsStringInRequests() {
        CreateExpenseRequest request = json.readValue("""
                {"amount":"1234.56","merchant":"Carrefour","paymentMethod":"Tarjeta"}""",
                CreateExpenseRequest.class);

        assertThat(request.amount()).isEqualByComparingTo("1234.56");
        assertThat(request.paymentMethod()).isEqualTo(PaymentMethodValue.TARJETA);
        // Sin fecha ni categoría: las resuelven el alta y el motor de reglas.
        assertThat(request.expenseDate()).isNull();
        assertThat(request.categoryId()).isNull();
    }

    @Test
    void paymentMethodsTravelInSpanish() {
        String body = json.writeValueAsString(expenseResponse(new BigDecimal("100.00")));

        assertThat(body).contains("\"paymentMethod\":\"Tarjeta\"");
    }

    @Test
    void anUnknownPaymentMethodIsRejected() {
        assertThatThrownBy(() -> json.readValue("""
                {"amount":"100.00","merchant":"Carrefour","paymentMethod":"Cripto"}""",
                CreateExpenseRequest.class))
                .hasMessageContaining("Medio de pago no admitido");
    }

    @Test
    void datesAndInstantsUseIsoFormat() {
        String body = json.writeValueAsString(expenseResponse(new BigDecimal("100.00")));

        assertThat(body)
                .contains("\"expenseDate\":\"2026-09-21\"")
                .contains("\"createdAt\":\"2026-09-21T13:45:00Z\"");
    }

    @Test
    void theMonthOfTheSeriesTravelsAsYearAndMonth() {
        String body = json.writeValueAsString(
                new MonthlyPoint(YearMonth.of(2026, 9), new BigDecimal("154320.00")));

        assertThat(body).contains("\"month\":\"2026-09\"").contains("\"total\":\"154320.00\"");
    }

    private static ExpenseResponse expenseResponse(BigDecimal amount) {
        return new ExpenseResponse(
                UUID.randomUUID(),
                amount,
                "Carrefour",
                LocalDate.of(2026, 9, 21),
                PaymentMethodValue.TARJETA,
                null,
                null,
                UUID.randomUUID(),
                "Ana",
                Instant.parse("2026-09-21T13:45:00Z"),
                Instant.parse("2026-09-21T13:45:00Z"));
    }
}
