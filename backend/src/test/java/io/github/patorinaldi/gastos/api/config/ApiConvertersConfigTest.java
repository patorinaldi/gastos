package io.github.patorinaldi.gastos.api.config;

import io.github.patorinaldi.gastos.api.web.dto.PaymentMethodValue;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El conversor que necesita {@code ?paymentMethod=Tarjeta}. En el cuerpo lo resuelve Jackson; en un
 * parámetro de consulta, Spring convierte con {@code Enum.valueOf} y espera «TARJETA», así que sin
 * este conversor el valor que la API documenta sería el único que no funciona.
 */
class ApiConvertersConfigTest {

    private final Converter<String, PaymentMethodValue> converter =
            new ApiConvertersConfig().paymentMethodValueConverter();

    @Test
    void convertsTheLabelThatTheApiDocuments() {
        assertThat(converter.convert("Tarjeta")).isEqualTo(PaymentMethodValue.TARJETA);
    }

    /** Spring traduce esta excepción a 400, no a 500. */
    @Test
    void rejectsAnUnknownValue() {
        assertThatThrownBy(() -> converter.convert("Cripto"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
