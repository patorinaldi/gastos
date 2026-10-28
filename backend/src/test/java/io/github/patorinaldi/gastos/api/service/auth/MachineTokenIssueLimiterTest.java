package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.config.MachineTokenLimitProperties;
import io.github.patorinaldi.gastos.api.service.limit.TooManyAttemptsException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El limitador en sí, con un reloj controlado por la prueba. El recorrido por HTTP lo cubre
 * {@code MachineTokenManagementTest}.
 */
class MachineTokenIssueLimiterTest {

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_ISSUES = 3;

    private final AtomicLong nanos = new AtomicLong();
    private final MachineTokenIssueLimiter limiter = new MachineTokenIssueLimiter(
            new MachineTokenLimitProperties(10, WINDOW, MAX_ISSUES), nanos::get);

    private final UUID ana = UUID.randomUUID();

    // Emitir y revocar en un bucle nunca llega al tope de vigentes: este lo frena.
    @Test
    void superadoElTopeRechazaAEsaPersonaYNoALasDemas() {
        for (int i = 0; i < MAX_ISSUES; i++) {
            limiter.count(ana);
        }

        assertThatThrownBy(() -> limiter.count(ana)).isInstanceOf(TooManyAttemptsException.class);
        assertThatCode(() -> limiter.count(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void alCerrarLaVentanaVuelveAAceptar() {
        for (int i = 0; i < MAX_ISSUES; i++) {
            limiter.count(ana);
        }

        nanos.addAndGet(WINDOW.toNanos());

        assertThatCode(() -> limiter.count(ana)).doesNotThrowAnyException();
    }
}
