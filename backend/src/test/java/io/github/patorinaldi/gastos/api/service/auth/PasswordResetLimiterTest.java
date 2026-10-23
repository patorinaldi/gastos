package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.config.PasswordResetLimitProperties;
import io.github.patorinaldi.gastos.api.service.limit.TooManyAttemptsException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * El limitador en sí, con un reloj controlado por la prueba. El recorrido por HTTP lo cubre
 * {@code PasswordResetTest}.
 */
class PasswordResetLimiterTest {

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX_PER_EMAIL = 3;
    private static final int MAX_PER_IP = 5;
    private static final String EMAIL = "ana@ejemplo.test";
    private static final String IP = "203.0.113.7";
    private static final String OTRA_IP = "198.51.100.20";

    private final AtomicLong nanos = new AtomicLong();
    private final PasswordResetLimiter limiter = new PasswordResetLimiter(
            new PasswordResetLimitProperties(WINDOW, MAX_PER_EMAIL, MAX_PER_IP), nanos::get);

    // Es lo que protege la casilla de quien recibe: cambiar de dirección no permite seguir.
    @Test
    void superadoElTopePorCorreoRechazaEseCorreoDesdeCualquierDireccion() {
        for (int i = 0; i < MAX_PER_EMAIL; i++) {
            limiter.count(EMAIL, "10.0.0." + i);
        }

        assertBlocked(EMAIL, OTRA_IP);
        assertAllowed("otra@ejemplo.test", OTRA_IP);
    }

    @Test
    void elCorreoNoDistingueMayusculas() {
        for (int i = 0; i < MAX_PER_EMAIL; i++) {
            limiter.count(EMAIL, IP);
        }

        assertBlocked(EMAIL.toUpperCase(), OTRA_IP);
    }

    @Test
    void superadoElTopePorDireccionRechazaCualquierCorreoDesdeEsaDireccion() {
        for (int i = 0; i < MAX_PER_IP; i++) {
            limiter.count("persona" + i + "@ejemplo.test", IP);
        }

        assertBlocked("otra@ejemplo.test", IP);
        assertAllowed("otra@ejemplo.test", OTRA_IP);
    }

    @Test
    void alCerrarLaVentanaVuelveAAceptar() {
        for (int i = 0; i < MAX_PER_EMAIL; i++) {
            limiter.count(EMAIL, IP);
        }

        nanos.addAndGet(WINDOW.toNanos());

        assertAllowed(EMAIL, IP);
    }

    @Test
    void indicaCuandoSePuedeVolverAPedir() {
        for (int i = 0; i < MAX_PER_EMAIL; i++) {
            limiter.count(EMAIL, IP);
        }
        nanos.addAndGet(Duration.ofMinutes(10).toNanos());

        TooManyAttemptsException rejected =
                catchThrowableOfType(TooManyAttemptsException.class, () -> limiter.count(EMAIL, IP));

        assertThat(rejected.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("3000");
    }

    private void assertBlocked(String email, String ip) {
        assertThatThrownBy(() -> limiter.count(email, ip)).isInstanceOf(TooManyAttemptsException.class);
    }

    private void assertAllowed(String email, String ip) {
        assertThatCode(() -> limiter.count(email, ip)).doesNotThrowAnyException();
    }
}
