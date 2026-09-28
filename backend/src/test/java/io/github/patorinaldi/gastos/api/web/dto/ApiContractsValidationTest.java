package io.github.patorinaldi.gastos.api.web.dto;

import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.RegisterRequest;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.CreateExpenseRequest;
import io.github.patorinaldi.gastos.api.web.dto.HouseholdContracts.RedeemInvitationRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que la validación de los contratos rechaza antes de que la petición llegue a la lógica de
 * negocio. Las mismas reglas están en la base como restricciones; acá se las hace valer temprano,
 * para devolver un 400 explicativo en lugar de un error de integridad.
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

    @Test
    void aValidExpenseHasNoViolations() {
        assertThat(validate(new CreateExpenseRequest(
                new BigDecimal("1234.56"), "Carrefour", null, null, null))).isEmpty();
    }

    @Test
    void anAmountOfZeroIsRejected() {
        assertThat(validate(new CreateExpenseRequest(
                BigDecimal.ZERO, "Carrefour", null, null, null))).contains("amount");
    }

    // RN-06: dos decimales exactos. Un tercer decimal no se redondea, se rechaza.
    @Test
    void anAmountWithThreeDecimalsIsRejected() {
        assertThat(validate(new CreateExpenseRequest(
                new BigDecimal("1234.567"), "Carrefour", null, null, null))).contains("amount");
    }

    @Test
    void aBlankMerchantIsRejected() {
        assertThat(validate(new CreateExpenseRequest(
                new BigDecimal("100.00"), "   ", null, null, null))).contains("merchant");
    }

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

    // El canje deja los gastos en el hogar anterior y no se deshace: sin confirmación, no va.
    @Test
    void redeemingWithoutConfirmationIsRejected() {
        assertThat(validate(new RedeemInvitationRequest("GST-4K2P-9XZ", false))).contains("confirm");
    }

    @Test
    void redeemingWithConfirmationIsAccepted() {
        assertThat(validate(new RedeemInvitationRequest("GST-4K2P-9XZ", true))).isEmpty();
    }

    private static Set<String> validate(Object request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
