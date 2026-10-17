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
    private static final String OTRA_IP = "198.51.100.20";

    private final AtomicLong nanos = new AtomicLong();
    private final LoginAttemptLimiter limiter =
            new LoginAttemptLimiter(new LoginLimitProperties(WINDOW, 10, 50, 100), nanos::get);

    @Test
    void despuesDeDiezFallosConElMismoCorreoYDireccionRechazaElSiguiente() {
        fail(EMAIL, IP, 10);

        assertBlocked(EMAIL, IP);
    }

    @Test
    void elCorreoNoDistingueMayusculas() {
        fail(EMAIL, IP, 10);

        assertBlocked(EMAIL.toUpperCase(), IP);
    }

    // La base compara con upper(), que lleva "ſ" a "S" y "ı" a "I": las dos variantes encuentran
    // la cuenta de sofia, así que tienen que compartir su contador.
    @Test
    void lasVariantesUnicodeQueLaBaseIgualaCompartenContador() {
        fail("sofia@ejemplo.test", IP, 5);
        fail("ſofia@ejemplo.test", IP, 3);
        fail("sofıa@ejemplo.test", IP, 2);

        assertBlocked("sofia@ejemplo.test", IP);
    }

    // Es lo que distingue este límite del de RNF-06 original, por correo solo: quien prueba
    // contraseñas desde otra dirección no le bloquea el acceso al dueño.
    @Test
    void elBloqueoDeUnCorreoNoAlcanzaAOtraDireccion() {
        fail(EMAIL, IP, 10);

        assertAllowed(EMAIL, OTRA_IP);
    }

    @Test
    void despuesDeCincuentaFallosDesdeUnaDireccionRechazaCualquierCorreo() {
        failWithDistinctEmails(IP, 50);

        assertBlocked("otra@ejemplo.test", IP);
        assertAllowed("otra@ejemplo.test", OTRA_IP);
    }

    // Un ataque repartido entre muchas direcciones no pasa de 100 intentos por ventana sobre una
    // misma cuenta.
    @Test
    void despuesDeCienFallosConUnCorreoDesdeDistintasDireccionesRechazaElSiguiente() {
        for (int i = 0; i < 100; i++) {
            fail(EMAIL, "203.0.113." + i, 1);
        }

        assertBlocked(EMAIL, "198.51.100.1");
        assertAllowed("otra@ejemplo.test", "198.51.100.1");
    }

    // Un solo cliente suele tener el /64 entero: rotar dentro de él no da intentos nuevos.
    @Test
    void lasIpv6DelMismoPrefijo64CompartenContador() {
        for (int i = 1; i <= 10; i++) {
            fail(EMAIL, "2001:db8:1:2::" + Integer.toHexString(i), 1);
        }

        assertBlocked(EMAIL, "2001:db8:1:2:ffff:ffff:ffff:ffff");
        assertAllowed(EMAIL, "2001:db8:1:3::1");
    }

    @Test
    void laClaveDeUnaIpv6EsSuPrefijo64() {
        assertThat(LoginAttemptLimiter.addressKey("2001:db8:1:2:aaaa:bbbb:cccc:dddd")).isEqualTo("2001:db8:1:2:0:0:0:0/64");
        assertThat(LoginAttemptLimiter.addressKey("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(LoginAttemptLimiter.addressKey("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void alCerrarLaVentanaVuelveAAceptar() {
        fail(EMAIL, IP, 10);

        nanos.addAndGet(WINDOW.toNanos());

        assertAllowed(EMAIL, IP);
    }

    // La ventana arranca con el primer intento: los que siguen no la estiran.
    @Test
    void laVentanaSeCuentaDesdeElPrimerIntento() {
        fail(EMAIL, IP, 1);
        nanos.addAndGet(Duration.ofMinutes(14).toNanos());
        fail(EMAIL, IP, 9);

        assertThat(retryAfter(EMAIL, IP)).isEqualTo("60");

        nanos.addAndGet(Duration.ofMinutes(1).toNanos());
        assertAllowed(EMAIL, IP);
    }

    @Test
    void retryAfterEsElTiempoQueFaltaParaQueCierreLaVentana() {
        fail(EMAIL, IP, 10);
        nanos.addAndGet(Duration.ofMinutes(5).plusMillis(300).toNanos());

        // Quedan 9 min 59,7 s: se redondea hacia arriba.
        assertThat(retryAfter(EMAIL, IP)).isEqualTo("600");
    }

    // Si hay más de un límite superado, se puede volver a intentar recién cuando cierran todos.
    @Test
    void conDosLimitesSuperadosRetryAfterEsElQueCierraUltimo() {
        failWithDistinctEmails(IP, 40);
        nanos.addAndGet(Duration.ofMinutes(10).toNanos());
        fail(EMAIL, IP, 10);

        // El de la dirección cierra en 5 min; el del correo desde la dirección, en 15.
        assertThat(retryAfter(EMAIL, IP)).isEqualTo("900");
    }

    @Test
    void unaContrasenaCorrectaReiniciaElCorreoDesdeEsaDireccion() {
        fail(EMAIL, IP, 9);
        limiter.start(EMAIL, IP).succeeded();

        fail(EMAIL, IP, 10);

        assertBlocked(EMAIL, IP);
    }

    // Si el acierto reiniciara también la dirección, quien tiene una cuenta propia podría
    // alternar intentos contra otras con logins propios y no llegar nunca al tope.
    @Test
    void unaContrasenaCorrectaNoReiniciaLosFallosDeLaDireccion() {
        failWithDistinctEmails(IP, 49);
        limiter.start(EMAIL, IP).succeeded();
        fail("otra@ejemplo.test", IP, 1);

        assertBlocked("una-mas@ejemplo.test", IP);
    }

    // Si la base se cae, el usuario reintenta y nunca se llegó a probar su contraseña: esos
    // intentos no pueden dejarlo bloqueado cuando la base vuelve.
    @Test
    void unIntentoDescartadoNoCuenta() {
        for (int i = 0; i < 20; i++) {
            limiter.start(EMAIL, IP).discard();
        }

        assertAllowed(EMAIL, IP);
    }

    @Test
    void descartarDespuesDeUnAciertoNoDescuentaDosVeces() {
        fail(EMAIL, IP, 9);
        LoginAttemptLimiter.Attempt attempt = limiter.start(EMAIL, IP);
        attempt.succeeded();
        attempt.discard();

        fail(EMAIL, IP, 10);

        assertBlocked(EMAIL, IP);
    }

    // Un intento rechazado no suma: si sumara, insistir con un correo bloqueado terminaría
    // bloqueando la dirección entera.
    @Test
    void losIntentosRechazadosNoCuentanParaLaDireccion() {
        fail(EMAIL, IP, 10);
        for (int i = 0; i < 100; i++) {
            assertBlocked(EMAIL, IP);
        }

        assertAllowed("otra@ejemplo.test", IP);
    }

    // Tampoco crean contadores: con la dirección bloqueada, seguir probando correos nuevos no
    // les suma intentos, y no sirve para llenar los mapas y desplazar contadores reales.
    @Test
    void losIntentosRechazadosNoCuentanParaElCorreo() {
        failWithDistinctEmails(IP, 50);
        for (int i = 0; i < 100; i++) {
            assertBlocked(EMAIL, IP);
        }

        fail(EMAIL, OTRA_IP, 10);
        assertBlocked(EMAIL, OTRA_IP);
        // Si los 100 rechazos hubieran contado, el correo ya estaría bloqueado desde cualquier
        // dirección.
        assertAllowed(EMAIL, "192.0.2.1");
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

    private void failWithDistinctEmails(String ip, int times) {
        for (int i = 0; i < times; i++) {
            fail("persona" + i + "@ejemplo.test", ip, 1);
        }
    }

    private void assertBlocked(String email, String ip) {
        assertThatThrownBy(() -> limiter.start(email, ip)).isInstanceOf(LoginBlockedException.class);
    }

    private void assertAllowed(String email, String ip) {
        assertThatCode(() -> limiter.start(email, ip)).doesNotThrowAnyException();
    }

    private String retryAfter(String email, String ip) {
        LoginBlockedException blocked = catchThrowableOfType(LoginBlockedException.class,
                () -> limiter.start(email, ip));
        return blocked.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
    }
}
