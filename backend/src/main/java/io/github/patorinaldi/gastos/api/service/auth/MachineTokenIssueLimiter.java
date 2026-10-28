package io.github.patorinaldi.gastos.api.service.auth;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.patorinaldi.gastos.api.config.MachineTokenLimitProperties;
import io.github.patorinaldi.gastos.api.service.limit.AttemptCounter;
import io.github.patorinaldi.gastos.api.service.limit.PendingAttempt;
import io.github.patorinaldi.gastos.api.service.limit.TooManyAttemptsException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Límite de pedidos de emisión de tokens de cliente máquina por persona (RNF-33), en ventanas fijas
 * como los del login. Todos los pedidos cuentan, también los que emiten: lo que se limita es la
 * emisión, y con ella las pruebas de contraseña que la confirman.
 */
@Component
public class MachineTokenIssueLimiter {

    private static final Logger log = LoggerFactory.getLogger(MachineTokenIssueLimiter.class);

    private final AttemptCounter byUser;

    @Autowired
    public MachineTokenIssueLimiter(MachineTokenLimitProperties limits) {
        this(limits, Ticker.systemTicker());
    }

    MachineTokenIssueLimiter(MachineTokenLimitProperties limits, Ticker ticker) {
        this.byUser = new AttemptCounter(limits.window(), limits.maxIssuesPerUser(), ticker);
    }

    /**
     * Cuenta un pedido de emisión de {@code userId}.
     *
     * @throws TooManyAttemptsException si llegó al tope de la ventana
     */
    public void count(UUID userId) {
        // El pedido no se descarta nunca: queda contado.
        PendingAttempt.start(byUser.slot(userId.toString(), () -> log.warn(
                "Límite de emisiones de tokens de cliente máquina: usuario={}", userId)));
    }
}
