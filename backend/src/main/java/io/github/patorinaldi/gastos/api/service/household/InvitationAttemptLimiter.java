package io.github.patorinaldi.gastos.api.service.household;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.patorinaldi.gastos.api.config.InvitationLimitProperties;
import io.github.patorinaldi.gastos.api.service.limit.AddressKey;
import io.github.patorinaldi.gastos.api.service.limit.AttemptCounter;
import io.github.patorinaldi.gastos.api.service.limit.PendingAttempt;
import io.github.patorinaldi.gastos.api.service.limit.TooManyAttemptsException;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Límite de intentos fallidos con códigos de invitación (RNF-30): frena a quien prueba códigos al
 * azar para entrar a un hogar ajeno. La vista previa y el canje cuentan juntos, porque los dos
 * sirven para probar un código. Dos contadores, en ventanas fijas como los del login (ver
 * {@code LoginAttemptLimiter}):
 *
 * <ul>
 *   <li>por usuario: el código viaja con la sesión, así que quien prueba códigos no puede cambiar
 *       de dirección para seguir;</li>
 *   <li>por dirección IP, con un tope mayor: frena a quien reparte el tanteo entre varias cuentas
 *       propias.</li>
 * </ul>
 *
 * <p>Un intento fallido es un código que responde {@code 410}. El código del hogar propio
 * ({@code 409}) no cuenta, porque no revela nada que el usuario no sepa. Un código válido tampoco
 * cuenta, pero no reinicia los contadores: si los reiniciara, quien tenga un código válido de otra
 * cuenta propia podría alternarlo con el tanteo y no llegar nunca al tope.
 */
@Component
public class InvitationAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(InvitationAttemptLimiter.class);

    private final AttemptCounter byUser;
    private final AttemptCounter byIp;

    @Autowired
    public InvitationAttemptLimiter(InvitationLimitProperties limits) {
        this(limits, Ticker.systemTicker());
    }

    InvitationAttemptLimiter(InvitationLimitProperties limits, Ticker ticker) {
        this.byUser = new AttemptCounter(limits.window(), limits.maxFailuresPerUser(), ticker);
        this.byIp = new AttemptCounter(limits.window(), limits.maxFailuresPerIp(), ticker);
    }

    /**
     * Ejecuta {@code action}, que prueba un código, como un intento de {@code userId} desde
     * {@code ip}. Cuenta como fallido si {@code action} lanza {@link InvitationUnavailableException}.
     *
     * @param ip dirección del cliente, sin puerto
     * @throws TooManyAttemptsException si alguno de los contadores llegó a su tope, sin ejecutar
     *                                  {@code action}
     */
    public <T> T guard(UUID userId, String ip, Supplier<T> action) {
        String ipKey = AddressKey.of(ip);
        PendingAttempt attempt = PendingAttempt.start(
                byUser.slot(userId.toString(), () -> log.warn(
                        "Límite de intentos con códigos de invitación por usuario: usuario={}", userId)),
                byIp.slot(ipKey, () -> log.warn(
                        "Límite de intentos con códigos de invitación por dirección: ip={}", ipKey)));
        T result;
        try {
            result = action.get();
        } catch (InvitationUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            // El código propio, o algo ajeno al código, como una caída de la base.
            attempt.discard();
            throw e;
        }
        attempt.discard();
        return result;
    }
}
