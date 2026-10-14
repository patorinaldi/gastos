package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.config.LoginLimitProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * El limitador en sí, con un reloj controlado por la prueba. Los límites son los de
 * application.yaml; el recorrido por HTTP lo cubre {@code LoginLimitTest}.
 */
class LoginAttemptLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String EMAIL = "ana@ejemplo.test";
    private static final String IP = "203.0.113.7";

    private final AtomicLong nanos = new AtomicLong();
    private final LoginAttemptLimiter limiter =
            new LoginAttemptLimiter(new LoginLimitProperties(WINDOW, 10, 50), nanos::get);

    @Test
    void despuesDeDiezFallosConElMismoCorreoYDireccionRechazaElSiguiente() {
        fail(EMAIL, IP, 10);

        assertThatThrownBy(() -> limiter.start(EMAIL, IP)).isInstanceOf(LoginBlockedException.class);
    }

    @Test
    void elCorreoNoDistingueMayusculas() {
        fail(EMAIL, IP, 10);

        assertThatThrownBy(() -> limiter.start(EMAIL.toUpperCase(), IP))
                .isInstanceOf(LoginBlockedException.class);
    }

    // Es lo que distingue este límite del de RNF-06 original, por correo solo: quien prueba
    // contraseñas desde otra dirección no le bloquea el acceso al dueño.
    @Test
    void elBloqueoDeUnCorreoNoAlcanzaAOtraDireccion() {
        fail(EMAIL, IP, 10);

        assertThatCode(() -> limiter.start(EMAIL, "198.51.100.20")).doesNotThrowAnyException();
    }

    @Test
    void despuesDeCincuentaFallosDesdeUnaDireccionRechazaCualquierCorreo() {
        for (int i = 0; i < 50; i++) {
            fail("persona" + i + "@ejemplo.test", IP, 1);
        }

        assertThatThrownBy(() -> limiter.start("otra@ejemplo.test", IP)).isInstanceOf(LoginBlockedException.class);
        assertThatCode(() -> limiter.start("otra@ejemplo.test", "198.51.100.20")).doesNotThrowAnyException();
    }

    @Test
    void alCerrarLaVentanaVuelveAAceptar() {
        fail(EMAIL, IP, 10);

        nanos.addAndGet(WINDOW.toNanos());

        assertThatCode(() -> limiter.start(EMAIL, IP)).doesNotThrowAnyException();
    }

    // La ventana arranca con el primer intento: los que siguen no la estiran.
    @Test
    void laVentanaSeCuentaDesdeElPrimerIntento() {
        fail(EMAIL, IP, 1);
        nanos.addAndGet(Duration.ofMinutes(14).toNanos());
        fail(EMAIL, IP, 9);

        LoginBlockedException blocked = catchThrowableOfType(LoginBlockedException.class,
                () -> limiter.start(EMAIL, IP));
        assertThat(blocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");

        nanos.addAndGet(Duration.ofMinutes(1).toNanos());
        assertThatCode(() -> limiter.start(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void retryAfterEsElTiempoQueFaltaParaQueCierreLaVentana() {
        fail(EMAIL, IP, 10);
        nanos.addAndGet(Duration.ofMinutes(5).plusMillis(300).toNanos());

        LoginBlockedException blocked = catchThrowableOfType(LoginBlockedException.class,
                () -> limiter.start(EMAIL, IP));

        // Quedan 9 min 59,7 s: se redondea hacia arriba.
        assertThat(blocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("600");
    }

    @Test
    void unaContrasenaCorrectaReiniciaElCorreoDesdeEsaDireccion() {
        fail(EMAIL, IP, 9);
        limiter.start(EMAIL, IP).succeeded();

        fail(EMAIL, IP, 10);

        assertThatThrownBy(() -> limiter.start(EMAIL, IP)).isInstanceOf(LoginBlockedException.class);
    }

    // Si el acierto reiniciara también la dirección, quien tiene una cuenta propia podría
    // alternar intentos contra otras con logins propios y no llegar nunca al tope.
    @Test
    void unaContrasenaCorrectaNoReiniciaLosFallosDeLaDireccion() {
        for (int i = 0; i < 49; i++) {
            fail("persona" + i + "@ejemplo.test", IP, 1);
        }
        limiter.start(EMAIL, IP).succeeded();
        fail("otra@ejemplo.test", IP, 1);

        assertThatThrownBy(() -> limiter.start("una-mas@ejemplo.test", IP))
                .isInstanceOf(LoginBlockedException.class);
    }

    // Un intento rechazado no suma: si sumara, insistir con un correo bloqueado terminaría
    // bloqueando la dirección entera.
    @Test
    void losIntentosRechazadosNoCuentanParaLaDireccion() {
        fail(EMAIL, IP, 10);
        for (int i = 0; i < 100; i++) {
            assertThatThrownBy(() -> limiter.start(EMAIL, IP)).isInstanceOf(LoginBlockedException.class);
        }

        assertThatCode(() -> limiter.start("otra@ejemplo.test", IP)).doesNotThrowAnyException();
    }

    @Test
    void retryAfterNuncaEsCero() {
        assertThat(new LoginBlockedException(Duration.ZERO).getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
                .isEqualTo("1");
    }

    /** {@code times} intentos con la contraseña incorrecta: empiezan y nunca se informan como acierto. */
    private void fail(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.start(email, ip);
        }
    }
}
