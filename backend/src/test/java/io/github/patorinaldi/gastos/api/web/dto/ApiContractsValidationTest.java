package io.github.patorinaldi.gastos.api.web.dto;

import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.CreateMachineTokenRequest;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.ForgotPasswordRequest;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.LoginRequest;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.RegisterRequest;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.ResendVerificationRequest;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.ResetPasswordRequest;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.VerifyEmailRequest;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.CreateExpenseRequest;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.ExpenseFilterRequest;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.UpdateExpenseRequest;
import io.github.patorinaldi.gastos.api.web.dto.HouseholdContracts.InvitationPreviewRequest;
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
        assertThat(validate(new UpdateExpenseRequest(null, null, null, null, null, null))).isEmpty();
    }

    @Test
    void aPartialUpdateCannotBlankTheMerchant() {
        assertThat(validate(new UpdateExpenseRequest(null, " ", null, null, null, null)))
                .contains("merchant");
    }

    // Asignar una categoría y devolver el gasto a la bandeja a la vez no tiene una lectura posible.
    @Test
    void anUpdateCannotAssignACategoryAndRemoveItAtOnce() {
        UUID category = UUID.randomUUID();

        assertThat(validate(new UpdateExpenseRequest(null, null, null, null, category, true)))
                .contains("categoryChangeConsistent");
        assertThat(validate(new UpdateExpenseRequest(null, null, null, null, category, false))).isEmpty();
        assertThat(validate(new UpdateExpenseRequest(null, null, null, null, null, true))).isEmpty();
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
    void aPageOverTheLimitIsRejected() {
        assertThat(validate(filter(null, ExpenseContracts.MAX_PAGE, 100))).isEmpty();
        assertThat(validate(filter(null, ExpenseContracts.MAX_PAGE + 1, 100))).contains("page");
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

    // Postgres no puede guardar el carácter nulo en un texto: sin este control, un nombre con uno
    // llegaría al insert y volvería como error interno.
    @Test
    void aNameWithControlCharactersIsRejected() {
        assertThat(validate(new RegisterRequest("Ana\0", "ana@ejemplo.com", "contrasena-larga")))
                .contains("name");
        assertThat(validate(new RegisterRequest("Ana\nPerez", "ana@ejemplo.com", "contrasena-larga")))
                .contains("name");
    }

    @Test
    void aNameWithAccentsAndSpacesIsAccepted() {
        assertThat(validate(new RegisterRequest("María José Núñez", "ana@ejemplo.com", "contrasena-larga")))
                .isEmpty();
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

    // Como el nombre del alta: un NUL llegaría a la base y volvería como 500.
    @Test
    void aMachineTokenNameWithControlCharactersIsRejected() {
        assertThat(validate(new CreateMachineTokenRequest("Atajo\u0000", "contrasena-larga"))).contains("name");
        assertThat(validate(new CreateMachineTokenRequest("Atajo del teléfono", "contrasena-larga"))).isEmpty();
    }

    // Sin la contraseña, una sesión robada alcanzaría para emitir un acceso que no vence.
    @Test
    void aMachineTokenNeedsThePassword() {
        assertThat(validate(new CreateMachineTokenRequest("Atajo", ""))).contains("password");
        assertThat(validate(new CreateMachineTokenRequest("Atajo", "a".repeat(73)))).contains("password");
    }

    // \p{Cntrl} sin (?U) cubre solo los controles ASCII. Estos dejan un nombre que se ve vacío o al
    // revés, y en el listado no se sabría cuál revocar.
    @Test
    void namesWithUnicodeControlOrInvisibleCharactersAreRejected() {
        for (String name : new String[] {"Atajo\u0085", "‮onofelet", "​", " ", "  ", "Ata jo"}) {
            assertThat(validate(new CreateMachineTokenRequest(name, "contrasena-larga"))).as(name).contains("name");
            assertThat(validate(new RegisterRequest(name, "ana@ejemplo.com", "contrasena-larga"))).as(name).contains("name");
        }
    }

    // La unión de ancho cero arma los emoji compuestos, y no deja un nombre invisible.
    @Test
    void namesWithEmojiAreAccepted() {
        assertThat(validate(new CreateMachineTokenRequest("Familia 👨‍👩‍👧", "contrasena-larga"))).isEmpty();
        assertThat(validate(new RegisterRequest("Ana ✨", "ana@ejemplo.com", "contrasena-larga"))).isEmpty();
    }

    // Spring MVC registra el cuerpo de la respuesta con su toString en DEBUG y TRACE.
    @Test
    void theIssuedTokenAndThePasswordStayOutOfToString() {
        var response = new AuthContracts.CreatedMachineTokenResponse(
                UUID.randomUUID(), "Atajo", "gst_valor-secreto", java.time.Instant.now());
        assertThat(response.toString()).doesNotContain("gst_valor-secreto").contains("Atajo");
        assertThat(new CreateMachineTokenRequest("Atajo", "clave-secreta").toString()).doesNotContain("clave-secreta");
    }

    @Test
    void anOversizedVerificationTokenIsRejected() {
        assertThat(validate(new VerifyEmailRequest("t".repeat(65)))).contains("token");
    }

    @Test
    void anOversizedEmailIsRejectedWhenAskingToResendTheVerification() {
        assertThat(validate(new ResendVerificationRequest("a".repeat(310) + "@ejemplo.com"))).contains("email");
    }

    @Test
    void anOversizedEmailIsRejectedWhenAskingForAReset() {
        String email = "a".repeat(310) + "@ejemplo.com";

        assertThat(validate(new ForgotPasswordRequest(email))).contains("email");
    }

    // El token se hashea antes de buscarlo, igual que un código de invitación: lleva tope.
    @Test
    void anOversizedResetTokenIsRejected() {
        assertThat(validate(new ResetPasswordRequest("t".repeat(65), "contrasena-larga")))
                .contains("token");
    }

    @Test
    void aResetWithAShortPasswordIsRejected() {
        assertThat(validate(new ResetPasswordRequest("t".repeat(43), "corta"))).contains("password");
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

    // El código se hashea completo antes de buscarlo: sin tope, cualquier sesión hace que el
    // servidor procese cuerpos arbitrariamente grandes. 32 deja margen para lo que se tipea de más
    // (espacios, guiones) sobre los 12 caracteres del formato.
    @Test
    void anOversizedInvitationCodeIsRejectedOnPreview() {
        assertThat(validate(new InvitationPreviewRequest("G".repeat(33)))).contains("code");
    }

    @Test
    void anOversizedInvitationCodeIsRejectedOnRedeem() {
        assertThat(validate(new RedeemInvitationRequest("G".repeat(33), true))).contains("code");
    }

    @Test
    void anInvitationCodeAtTheLimitIsAcceptedOnPreview() {
        assertThat(validate(new InvitationPreviewRequest("G".repeat(32)))).isEmpty();
    }

    @Test
    void anInvitationCodeAtTheLimitIsAcceptedOnRedeem() {
        assertThat(validate(new RedeemInvitationRequest("G".repeat(32), true))).isEmpty();
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────

    private static CreateExpenseRequest expense(BigDecimal amount, String merchant) {
        return new CreateExpenseRequest(amount, merchant, null, null, null);
    }

    // Las reglas del filtro son las mismas en el listado y en el análisis (RF-29).
    @Test
    void anInvertedPeriodIsRejectedInTheListingAsInTheAnalysis() {
        java.time.LocalDate first = java.time.LocalDate.of(2026, 9, 1);
        java.time.LocalDate last = java.time.LocalDate.of(2026, 9, 30);

        assertThat(validate(new ExpenseFilterRequest(last, first, null, null, null, null, null)))
                .contains("periodOrdered");
        assertThat(validate(new AnalysisContracts.AnalysisFilterRequest(last, first, null, null, null)))
                .contains("periodOrdered");
        assertThat(validate(new ExpenseFilterRequest(first, last, null, null, null, null, null))).isEmpty();
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
