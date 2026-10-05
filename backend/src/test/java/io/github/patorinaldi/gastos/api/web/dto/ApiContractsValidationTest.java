package io.github.patorinaldi.gastos.api.web.dto;

import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.LoginRequest;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.RegisterRequest;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.CreateExpenseRequest;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.ExpenseFilterRequest;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.UpdateExpenseRequest;
import io.github.patorinaldi.gastos.api.web.dto.HouseholdContracts.RedeemInvitationRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que la validación de los contratos rechaza antes de que la petición llegue a la lógica de
 * negocio. Las mismas reglas están en la base como restricciones; acá se las hace valer temprano,
 * para devolver un 400 explicativo en lugar de un error de integridad que vuelve como 500.
 */
class ApiContractsValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    // ── Gastos ──────────────────────────────────────────────────────────────

    @Test
    void aValidExpenseHasNoViolations() {
        assertThat(validate(expense(new BigDecimal("1234.56"), "Carrefour"))).isEmpty();
    }

    @Test
    void anAmountOfZeroIsRejected() {
        assertThat(validate(expense(BigDecimal.ZERO, "Carrefour"))).contains("amount");
    }

    // RN-06: dos decimales exactos. Un tercer decimal no se redondea, se rechaza.
    @Test
    void anAmountWithThreeDecimalsIsRejected() {
        assertThat(validate(expense(new BigDecimal("1234.567"), "Carrefour"))).contains("amount");
    }

    @Test
    void aBlankMerchantIsRejected() {
        assertThat(validate(expense(new BigDecimal("100.00"), "   "))).contains("merchant");
    }

    // La columna es obligatoria y no tiene default en la base: el contrato pone el que falta.
    @Test
    void anExpenseWithoutPaymentMethodDefaultsToCash() {
        CreateExpenseRequest request = expense(new BigDecimal("100.00"), "Carrefour");

        assertThat(request.paymentMethod()).isEqualTo(PaymentMethodValue.EFECTIVO);
    }

    // En una modificación parcial, null es "no cambiar" y es válido; " " sí es un cambio, y uno
    // que la base rechaza.
    @Test
    void aPartialUpdateMayOmitEverything() {
        assertThat(validate(new UpdateExpenseRequest(null, null, null, null, null))).isEmpty();
    }

    @Test
    void aPartialUpdateCannotBlankTheMerchant() {
        assertThat(validate(new UpdateExpenseRequest(null, " ", null, null, null)))
                .contains("merchant");
    }

    // ── Filtros del listado ─────────────────────────────────────────────────

    @Test
    void aListingWithoutPaginationUsesTheDefaults() {
        ExpenseFilterRequest filter = filter(null, null, null);

        assertThat(filter.page()).isZero();
        assertThat(filter.size()).isEqualTo(ExpenseContracts.DEFAULT_PAGE_SIZE);
        assertThat(validate(filter)).isEmpty();
    }

    @Test
    void aPageSizeOverTheLimitIsRejected() {
        assertThat(validate(filter(null, 0, 101))).contains("size");
    }

    @Test
    void aPageSizeOfZeroIsRejectedWhenItIsAsked() {
        assertThat(validate(filter(null, 0, 0))).contains("size");
    }

    @Test
    void theInboxIsAValidCategoryFilter() {
        assertThat(validate(filter(ExpenseContracts.UNCATEGORIZED, null, null))).isEmpty();
        assertThat(filter(ExpenseContracts.UNCATEGORIZED, null, null).isUncategorized()).isTrue();
    }

    @Test
    void aCategoryFilterThatIsNeitherAnIdNorTheInboxIsRejected() {
        assertThat(validate(filter("foo", null, null))).contains("categoryId");
    }

    @Test
    void aCategoryFilterWithAnIdentifierIsAccepted() {
        assertThat(validate(filter(UUID.randomUUID().toString(), null, null))).isEmpty();
    }

    // ── Identidad ───────────────────────────────────────────────────────────

    @Test
    void aShortPasswordIsRejected() {
        assertThat(validate(new RegisterRequest("Ana", "ana@ejemplo.com", "corta")))
                .contains("password");
    }

    @Test
    void anInvalidEmailIsRejected() {
        assertThat(validate(new RegisterRequest("Ana", "ana", "contrasena-larga")))
                .contains("email");
    }

    // 72 caracteres ASCII son 72 bytes: es lo máximo que bcrypt lee, y entra justo.
    @Test
    void aPasswordOfSeventyTwoAsciiCharactersIsAccepted() {
        assertThat(validate(new RegisterRequest("Ana", "ana@ejemplo.com", "a".repeat(72))))
                .isEmpty();
    }

    // Las mismas 72 letras con tilde son 144 bytes. Medido en caracteres esto pasaría, y después
    // bcrypt cortaría la contraseña por la mitad sin que nadie se entere.
    @Test
    void aPasswordOfSeventyTwoAccentedCharactersIsRejected() {
        assertThat(validate(new RegisterRequest("Ana", "ana@ejemplo.com", "á".repeat(72))))
                .contains("password");
    }

    // El login no está autenticado: sin tope, cualquiera hace que el servidor cifre kilobytes.
    @Test
    void anOversizedPasswordIsRejectedAtLogin() {
        assertThat(validate(new LoginRequest("ana@ejemplo.com", "a".repeat(10_000))))
                .contains("password");
    }

    @Test
    void aShortPasswordIsNotRejectedAtLogin() {
        assertThat(validate(new LoginRequest("ana@ejemplo.com", "corta"))).isEmpty();
    }

    // ── Hogares ─────────────────────────────────────────────────────────────

    // El canje deja los gastos en el hogar anterior y no se deshace: sin confirmación, no va.
    @Test
    void redeemingWithoutConfirmationIsRejected() {
        assertThat(validate(new RedeemInvitationRequest("GST-4K2P-9XZ", false))).contains("confirm");
    }

    @Test
    void redeemingWithConfirmationIsAccepted() {
        assertThat(validate(new RedeemInvitationRequest("GST-4K2P-9XZ", true))).isEmpty();
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────

    private static CreateExpenseRequest expense(BigDecimal amount, String merchant) {
        return new CreateExpenseRequest(amount, merchant, null, null, null);
    }

    private static ExpenseFilterRequest filter(String categoryId, Integer page, Integer size) {
        return new ExpenseFilterRequest(null, null, categoryId, null, null, page, size);
    }

    private static Set<String> validate(Object request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
