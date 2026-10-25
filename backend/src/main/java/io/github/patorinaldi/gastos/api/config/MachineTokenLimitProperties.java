package io.github.patorinaldi.gastos.api.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Límites sobre los tokens de cliente máquina (RNF-33). Cada token vigente es una forma más de
 * cargar gastos a su nombre, que no vence: el tope de vigentes acota cuántos quedan dando vueltas si
 * una sesión robada se pone a emitirlos. El de emisiones acota el resto: sin él, emitir y revocar en
 * un bucle nunca llega al tope de vigentes y deja una fila revocada por vuelta, y cada emisión corre
 * bcrypt para confirmar la contraseña.
 *
 * @param maxActivePerUser tokens sin revocar que puede tener una persona en su hogar
 * @param window           ventana en la que se cuentan las emisiones, desde la primera
 * @param maxIssuesPerUser pedidos de emisión de una persona por ventana, salgan bien o no
 */
@Validated
@ConfigurationProperties("gastos.machine-token-limit")
public record MachineTokenLimitProperties(
        @Positive int maxActivePerUser,
        @NotNull @DurationMin(seconds = 1) Duration window,
        @Positive int maxIssuesPerUser) {
}
