package io.github.patorinaldi.gastos.api.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Límites sobre los códigos de invitación (RNF-30). Atacan el mismo riesgo, adivinar un código
 * vigente: los intentos fallidos acotan cuántos códigos se pueden probar, y el máximo de
 * invitaciones vigentes, cuántos hay para acertar en cada hogar.
 *
 * <p>La ventana tiene un mínimo de un segundo, por lo mismo que {@link LoginLimitProperties}.
 *
 * @param window                ventana en la que se cuentan los intentos, desde el primero
 * @param maxFailuresPerUser    códigos inválidos que puede probar un mismo usuario
 * @param maxFailuresPerIp      códigos inválidos que se pueden probar desde una misma dirección
 * @param maxActivePerHousehold invitaciones sin canjear ni vencer que puede tener un hogar
 */
@Validated
@ConfigurationProperties("gastos.invitation-limit")
public record InvitationLimitProperties(
        @NotNull @DurationMin(seconds = 1) Duration window,
        @Positive int maxFailuresPerUser,
        @Positive int maxFailuresPerIp,
        @Positive int maxActivePerHousehold) {
}
