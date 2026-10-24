package io.github.patorinaldi.gastos.api.service.auth;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.patorinaldi.gastos.api.config.PasswordResetLimitProperties;
import io.github.patorinaldi.gastos.api.service.limit.AddressKey;
import io.github.patorinaldi.gastos.api.service.limit.AttemptCounter;
import io.github.patorinaldi.gastos.api.service.limit.PendingAttempt;
import io.github.patorinaldi.gastos.api.service.limit.TooManyAttemptsException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Límite de pedidos de restablecimiento de contraseña (RNF-32). Cada pedido puede mandar un correo,
 * así que sin límite cualquiera podría llenar la casilla de otra persona o gastar la cuota del
 * proveedor de envío. Dos contadores, en ventanas fijas como los del login:
 *
 * <ul>
 *   <li>por correo, desde cualquier dirección: es lo que protege la casilla de quien recibe;</li>
 *   <li>por dirección, con cualquier correo: frena a quien recorre una lista de correos.</li>
 * </ul>
 *
 * <p>Todos los pedidos cuentan, también los que terminan en un correo enviado: acá no hay intento
 * fallido, lo que se limita es el envío. Se cuentan por el correo escrito, exista o no la cuenta,
 * así que el 429 no revela si el correo está registrado.
 */
@Component
public class PasswordResetLimiter {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetLimiter.class);

    private final AttemptCounter byEmail;
    private final AttemptCounter byIp;

    @Autowired
    public PasswordResetLimiter(PasswordResetLimitProperties limits) {
        this(limits, Ticker.systemTicker());
    }

    PasswordResetLimiter(PasswordResetLimitProperties limits, Ticker ticker) {
        this.byEmail = new AttemptCounter(limits.window(), limits.maxPerEmail(), ticker);
        this.byIp = new AttemptCounter(limits.window(), limits.maxPerIp(), ticker);
    }

    /**
     * Cuenta un pedido para {@code email} desde {@code ip}.
     *
     * @param ip dirección del cliente, sin puerto
     * @throws TooManyAttemptsException si alguno de los contadores llegó a su tope
     */
    public void count(String email, String ip) {
        String emailKey = email.toUpperCase(Locale.ROOT);
        String ipKey = AddressKey.of(ip);
        // El pedido no se descarta nunca: queda contado.
        PendingAttempt.start(
                byEmail.slot(emailKey, () -> log.warn(
                        "Límite de pedidos de restablecimiento por correo: correo={}", LogHash.of(emailKey))),
                byIp.slot(ipKey, () -> log.warn(
                        "Límite de pedidos de restablecimiento por dirección: ip={}", ipKey)));
    }
}
