package io.github.patorinaldi.gastos.api.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Límite de pedidos de enlaces por correo: restablecimiento de contraseña y reenvío de verificación
 * (RNF-32). La ventana tiene un mínimo de un segundo, por lo mismo que {@link LoginLimitProperties}.
 *
 * @param window      ventana en la que se cuentan los pedidos, desde el primero
 * @param maxPerEmail pedidos para un mismo correo, desde cualquier dirección
 * @param maxPerIp    pedidos desde una misma dirección, con cualquier correo
 */
@Validated
@ConfigurationProperties("gastos.email-link-limit")
public record EmailLinkLimitProperties(
        @NotNull @DurationMin(seconds = 1) Duration window,
        @Positive int maxPerEmail,
        @Positive int maxPerIp) {
}
