package io.github.patorinaldi.gastos.api.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Límite de intentos fallidos de inicio de sesión (RNF-06).
 *
 * @param window              ventana en la que se cuentan los intentos, desde el primero
 * @param maxPerEmailAndIp    intentos fallidos con un mismo correo desde una misma dirección
 * @param maxPerIp            intentos fallidos desde una misma dirección, con cualquier correo
 */
@Validated
@ConfigurationProperties("gastos.login-limit")
public record LoginLimitProperties(
        @NotNull Duration window,
        @Positive int maxPerEmailAndIp,
        @Positive int maxPerIp) {
}