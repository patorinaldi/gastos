package io.github.patorinaldi.gastos.api.service.household;

import io.github.patorinaldi.gastos.api.config.InvitationLimitProperties;
import io.github.patorinaldi.gastos.api.service.limit.TooManyAttemptsException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El limitador de códigos de invitación en sí, con un reloj controlado por la prueba. Los límites
 * son los de application.yaml; el recorrido por HTTP lo cubre {@code InvitationLimitTest}. El
 * conteo en ventanas fijas es el mismo del login, que cubre {@code LoginAttemptLimiterTest}.
 */
class InvitationAttemptLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String IP = "203.0.113.7";
    private static final String OTRA_IP = "198.51.100.20";

    private final AtomicLong nanos = new AtomicLong();
    private final InvitationAttemptLimiter limiter =
            new InvitationAttemptLimiter(new InvitationLimitProperties(WINDOW, 10, 50, 10), nanos::get);
    private final UUID ana = UUID.randomUUID();

    @Test
    void despuesDeDiezCodigosInvalidosRechazaElSiguienteSinProbarlo() {
        fail(ana, IP, 10);

        AtomicBoolean probado = new AtomicBoolean();
        assertThatThrownBy(() -> limiter.guard(ana, IP, () -> probado.getAndSet(true)))
                .isInstanceOf(TooManyAttemptsException.class);
        assertThat(probado).isFalse();
    }

    // Cambiar de dirección no le da intentos nuevos a la misma cuenta.
    @Test
    void elBloqueoDelUsuarioAlcanzaATodasSusDirecciones() {
        fail(ana, IP, 10);

        assertBlocked(ana, OTRA_IP);
    }

    @Test
    void elBloqueoDeUnUsuarioNoAlcanzaAOtroDesdeLaMismaDireccion() {
        fail(ana, IP, 10);

        assertAllowed(UUID.randomUUID(), IP);
    }

    // Repartir el tanteo entre varias cuentas propias no pasa de 50 por dirección.
    @Test
    void despuesDeCincuentaCodigosInvalidosDesdeUnaDireccionRechazaACualquierUsuario() {
        for (int i = 0; i < 50; i++) {
            fail(UUID.randomUUID(), IP, 1);
        }

        assertBlocked(UUID.randomUUID(), IP);
        assertAllowed(UUID.randomUUID(), OTRA_IP);
    }

    @Test
    void unCodigoValidoNoCuenta() {
        for (int i = 0; i < 20; i++) {
            limiter.guard(ana, IP, () -> "ok");
        }

        assertAllowed(ana, IP);
    }

    // Si un código válido reiniciara el contador, quien tenga uno de otra cuenta propia podría
    // alternarlo con el tanteo y no llegar nunca al tope.
    @Test
    void unCodigoValidoNoReiniciaLosFallos() {
        fail(ana, IP, 9);
        limiter.guard(ana, IP, () -> "ok");
        fail(ana, IP, 1);

        assertBlocked(ana, IP);
    }

    // El 409 solo revela el hogar propio: no es un intento de adivinar.
    @Test
    void elCodigoDelHogarPropioNoCuenta() {
        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() -> limiter.guard(ana, IP, () -> {
                throw new AlreadyInHouseholdException();
            })).isInstanceOf(AlreadyInHouseholdException.class);
        }

        assertAllowed(ana, IP);
    }

    // Si la base se cae, el código no se llegó a probar.
    @Test
    void unErrorAjenoAlCodigoNoCuenta() {
        for (int i = 0; i < 20; i++) {
            assertThatThrownBy(() -> limiter.guard(ana, IP, () -> {
                throw new IllegalStateException("la base no responde");
            })).isInstanceOf(IllegalStateException.class);
        }

        assertAllowed(ana, IP);
    }

    @Test
    void alCerrarLaVentanaVuelveAAceptar() {
        fail(ana, IP, 10);

        nanos.addAndGet(WINDOW.toNanos());

        assertAllowed(ana, IP);
    }

    /** {@code times} códigos inválidos: el servicio responde 410. */
    private void fail(UUID user, String ip, int times) {
        for (int i = 0; i < times; i++) {
            assertThatThrownBy(() -> limiter.guard(user, ip, () -> {
                throw new InvitationUnavailableException();
            })).isInstanceOf(InvitationUnavailableException.class);
        }
    }

    private void assertBlocked(UUID user, String ip) {
        assertThatThrownBy(() -> limiter.guard(user, ip, () -> "ok")).isInstanceOf(TooManyAttemptsException.class);
    }

    private void assertAllowed(UUID user, String ip) {
        assertThatCode(() -> limiter.guard(user, ip, () -> "ok")).doesNotThrowAnyException();
    }
}
