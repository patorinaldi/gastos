package io.github.patorinaldi.gastos.api.config;

import io.github.patorinaldi.gastos.api.web.dto.PaymentMethodValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

/**
 * Conversión de los parámetros de consulta que no alcanza a resolver Spring por su cuenta.
 *
 * <p>En el cuerpo de una petición, Jackson construye {@link PaymentMethodValue} con su
 * {@code JsonCreator} y acepta «Tarjeta». En un parámetro de consulta no interviene Jackson: Spring
 * convierte con {@code Enum.valueOf}, que espera el nombre de la constante («TARJETA») y rechaza el
 * valor del dominio. Sin este conversor, {@code ?paymentMethod=Tarjeta} responde 400 pese a ser
 * exactamente el valor que la API documenta y devuelve.
 */
@Configuration
class ApiConvertersConfig {

    /** Un valor desconocido lanza IllegalArgumentException, que Spring traduce a 400. */
    @Bean
    Converter<String, PaymentMethodValue> paymentMethodValueConverter() {
        return PaymentMethodValue::from;
    }
}
