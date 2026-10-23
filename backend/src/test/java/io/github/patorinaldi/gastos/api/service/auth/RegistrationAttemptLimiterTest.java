package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.config.RegistrationLimitProperties;
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
 * {@code RegisterTest}.
 */
class RegistrationAttemptLimiterTest {

    private static final Duration WINDOW = Duration.ofHours(1);
    private static final int MAX = 3;
    private static final String IP = "203.0.113.7";
    private static final String OTRA_IP = "198.51.100.20";

    private final AtomicLong nanos = new AtomicLong();
    private final RegistrationAttemptLimiter limiter =
            new RegistrationAttemptLimiter(new RegistrationLimitProperties(WINDOW, MAX), nanos::get);

    @Test
    void aceptaHastaElTopeYRechazaElSiguiente() {
        intentar(IP, MAX);

        assertThatThrownBy(() -> limiter.start(IP)).isInstanceOf(TooManyAttemptsException.class);
    }

    @Test
    void elBloqueoDeUnaDireccionNoAlcanzaAOtra() {
        intentar(IP, MAX);

        assertThatCode(() -> limiter.start(OTRA_IP)).doesNotThrowAnyException();
    }

    @Test
    void cuandoCierraLaVentanaLaDireccionVuelveAPoderRegistrar() {
        intentar(IP, MAX);

        nanos.addAndGet(WINDOW.toNanos() + 1);

        assertThatCode(() -> limiter.start(IP)).doesNotThrowAnyException();
    }

    // Retry-After le dice a quien llama cuándo volver; con un 0 invitaría a reintentar enseguida.
    @Test
    void elRechazoIndicaCuantoFaltaParaQueCierreLaVentana() {
        intentar(IP, MAX);
        nanos.addAndGet(Duration.ofMinutes(10).toNanos());

        TooManyAttemptsException rechazo = catchThrowableOfType(
                TooManyAttemptsException.class, () -> limiter.start(IP));

        assertThat(rechazo.getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .isEqualTo(String.valueOf(Duration.ofMinutes(50).toSeconds()));
    }

    // Un intento rechazado no suma: si sumara, insistir con una dirección bloqueada alargaría el
    // bloqueo en lugar de esperarlo.
    @Test
    void insistirConUnaDireccionBloqueadaNoAlargaElBloqueo() {
        intentar(IP, MAX);
        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> limiter.start(IP)).isInstanceOf(TooManyAttemptsException.class);
        }

        nanos.addAndGet(WINDOW.toNanos() + 1);

        assertThatCode(() -> limiter.start(IP)).doesNotThrowAnyException();
    }

    private void intentar(String ip, int veces) {
        for (int i = 0; i < veces; i++) {
            limiter.start(ip);
        }
    }
}
