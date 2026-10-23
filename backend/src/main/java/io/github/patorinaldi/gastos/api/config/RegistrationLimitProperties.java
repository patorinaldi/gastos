package io.github.patorinaldi.gastos.api.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Límite de altas de cuenta por dirección IP (RNF-31).
 *
 * <p>La ventana tiene un mínimo de un segundo, por lo mismo que {@link LoginLimitProperties}.
 *
 * @param window   ventana en la que se cuentan los intentos, desde el primero
 * @param maxPerIp intentos de alta que se aceptan desde una misma dirección en la ventana
 */
@Validated
@ConfigurationProperties("gastos.registration-limit")
public record RegistrationLimitProperties(
        @NotNull @DurationMin(seconds = 1) Duration window,
        @Positive int maxPerIp) {
}
