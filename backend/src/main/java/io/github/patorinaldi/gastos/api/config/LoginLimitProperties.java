package io.github.patorinaldi.gastos.api.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Límite de intentos fallidos de inicio de sesión (RNF-06).
 *
 * <p>La ventana tiene un mínimo de un segundo: con cero, cada contador vencería apenas se crea y
 * el límite quedaría apagado sin que nada lo avise. Así, la aplicación no arranca.
 *
 * @param window           ventana en la que se cuentan los intentos, desde el primero
 * @param maxPerEmailAndIp intentos fallidos con un mismo correo desde una misma dirección
 * @param maxPerIp         intentos fallidos desde una misma dirección, con cualquier correo
 * @param maxPerEmail      intentos fallidos con un mismo correo, desde cualquier dirección
 */
@Validated
@ConfigurationProperties("gastos.login-limit")
public record LoginLimitProperties(
        @NotNull @DurationMin(seconds = 1) Duration window,
        @Positive int maxPerEmailAndIp,
        @Positive int maxPerIp,
        @Positive int maxPerEmail) {
}
