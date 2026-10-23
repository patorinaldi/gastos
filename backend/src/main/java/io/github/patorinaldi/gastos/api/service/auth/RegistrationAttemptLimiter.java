package io.github.patorinaldi.gastos.api.service.auth;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.patorinaldi.gastos.api.config.RegistrationLimitProperties;
import io.github.patorinaldi.gastos.api.service.limit.AddressKey;
import io.github.patorinaldi.gastos.api.service.limit.AttemptCounter;
import io.github.patorinaldi.gastos.api.service.limit.PendingAttempt;
import io.github.patorinaldi.gastos.api.service.limit.TooManyAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Límite de altas de cuenta por dirección IP (RNF-31).
 *
 * <p>El punto de entrada es público y cada llamada que llega al alta corre bcrypt, así que sin
 * límite sirve para dos cosas: agotar el procesador del servidor, y averiguar qué correos tienen
 * cuenta, porque el alta responde 409 cuando el correo ya existe (RN-01). El 409 es parte del
 * contrato y no se puede callar; lo que sí se puede es encarecer el sondeo.
 *
 * <p>A diferencia del login y de los códigos de invitación, <strong>todos los intentos cuentan</strong>,
 * también los que terminan bien: lo que se limita es el alta en sí, y no hay una contraseña ni un
 * código que acertar. Por eso el intento nunca se descuenta. No hay contador por correo: repetir un
 * correo solo devuelve 409, y bloquearlo le permitiría a un tercero dejar sin alta a su dueño.
 *
 * <p>Los contadores viven en memoria, como los del resto de los límites (ver
 * {@link AttemptCounter}).
 */
@Component
public class RegistrationAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(RegistrationAttemptLimiter.class);

    private final AttemptCounter byIp;

    @Autowired
    public RegistrationAttemptLimiter(RegistrationLimitProperties limits) {
        this(limits, Ticker.systemTicker());
    }

    RegistrationAttemptLimiter(RegistrationLimitProperties limits, Ticker ticker) {
        this.byIp = new AttemptCounter(limits.window(), limits.maxPerIp(), ticker);
    }

    /**
     * Cuenta un intento de alta desde {@code ip}.
     *
     * @param ip dirección del cliente, sin puerto
     * @throws TooManyAttemptsException si la dirección llegó al tope; el intento no se cuenta
     */
    public void start(String ip) {
        String ipKey = AddressKey.of(ip);
        PendingAttempt.start(byIp.slot(ipKey, () -> log.warn(
                "Límite de altas de cuenta por dirección: ip={}", ipKey)));
    }
}
