package io.github.patorinaldi.gastos.api.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Tope de tokens de cliente máquina vigentes por persona (RNF-33). Cada token vigente es una forma
 * más de cargar gastos a su nombre, que no vence: el tope acota cuántos quedan dando vueltas si una
 * sesión robada se pone a emitirlos.
 *
 * @param maxActivePerUser tokens sin revocar que puede tener una persona en su hogar
 */
@Validated
@ConfigurationProperties("gastos.machine-token-limit")
public record MachineTokenLimitProperties(@Positive int maxActivePerUser) {
}
