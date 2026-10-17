package io.github.patorinaldi.gastos.api.service.auth;

import com.github.benmanes.caffeine.cache.Ticker;
import io.github.patorinaldi.gastos.api.config.LoginLimitProperties;
import io.github.patorinaldi.gastos.api.service.limit.AddressKey;
import io.github.patorinaldi.gastos.api.service.limit.AttemptCounter;
import io.github.patorinaldi.gastos.api.service.limit.PendingAttempt;
import io.github.patorinaldi.gastos.api.service.limit.TooManyAttemptsException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Límite de intentos fallidos de inicio de sesión (RNF-06), con tres contadores:
 *
 * <ul>
 *   <li>por correo y dirección IP: frena a quien prueba contraseñas sobre una cuenta, sin que un
 *       tercero pueda bloquearle el acceso al dueño desde otra dirección;</li>
 *   <li>por dirección IP, con un tope mayor: frena a quien prueba muchos correos desde un mismo
 *       lugar;</li>
 *   <li>por correo, desde cualquier dirección, con el tope más alto: frena a quien reparte el
 *       ataque sobre una cuenta entre muchas direcciones. Es alto a propósito, porque cualquiera
 *       puede llenarlo y dejar afuera al dueño, y así hacerlo exige un ataque deliberado.</li>
 * </ul>
 *
 * <p>Cada contador es una ventana fija que empieza con el primer intento y dura
 * {@link LoginLimitProperties#window()}. Superado un tope, los intentos se rechazan hasta que la
 * ventana cierra, sin comparar la contraseña.
 *
 * <p>Un intento cuenta como fallido desde que empieza y deja de contar si la contraseña resulta
 * correcta, o si falla por otra causa que no son las credenciales. Ver {@link PendingAttempt}.
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    private final AttemptCounter byEmailAndIp;
    private final AttemptCounter byIp;
    private final AttemptCounter byEmail;

    @Autowired
    public LoginAttemptLimiter(LoginLimitProperties limits) {
        this(limits, Ticker.systemTicker());
    }

    LoginAttemptLimiter(LoginLimitProperties limits, Ticker ticker) {
        this.byEmailAndIp = new AttemptCounter(limits.window(), limits.maxPerEmailAndIp(), ticker);
        this.byIp = new AttemptCounter(limits.window(), limits.maxPerIp(), ticker);
        this.byEmail = new AttemptCounter(limits.window(), limits.maxPerEmail(), ticker);
    }

    /**
     * Registra un intento de inicio de sesión.
     *
     * @param ip dirección del cliente, sin puerto
     * @throws TooManyAttemptsException si alguno de los contadores llegó a su tope
     */
    public Attempt start(String email, String ip) {
        // Mayúsculas, como compara la base (findByEmailIgnoreCase usa upper): en minúsculas,
        // variantes como "ſofia" o "sofıa" encontrarían la misma cuenta con otro contador.
        String emailKey = email.toUpperCase(Locale.ROOT);
        String ipKey = AddressKey.of(ip);
        String pairKey = emailKey + " " + ipKey;

        // El correo va como hash en el log (RNF-09), que alcanza para ver si los bloqueos repiten
        // cuenta.
        PendingAttempt attempt = PendingAttempt.start(
                byEmailAndIp.slot(pairKey, () -> log.warn(
                        "Límite de intentos de login por correo y dirección: correo={} ip={}",
                        hash(emailKey), ipKey)),
                byIp.slot(ipKey, () -> log.warn(
                        "Límite de intentos de login por dirección: ip={}", ipKey)),
                byEmail.slot(emailKey, () -> log.warn(
                        "Límite de intentos de login por correo, desde cualquier dirección: correo={}",
                        hash(emailKey))));
        return new Attempt(attempt);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 es obligatorio en toda JVM", e);
        }
    }

    /**
     * Un intento en curso. Termina con {@link #succeeded()} si la contraseña era correcta, o con
     * {@link #discard()} si falló por otra causa que no son las credenciales. Si no se informa
     * nada, queda como fallido.
     */
    public final class Attempt {

        private final PendingAttempt attempt;

        private Attempt(PendingAttempt attempt) {
            this.attempt = attempt;
        }

        /**
         * La contraseña era correcta: el correo vuelve a empezar de cero desde esa dirección (CU-02,
         * paso 5), y el intento deja de contar como fallido en los otros dos contadores, que no se
         * reinician. Si se reiniciara el de la dirección, quien tenga una cuenta propia podría
         * reiniciarlo entre intentos contra otras.
         */
        public void succeeded() {
            attempt.discardAndReset(byEmailAndIp);
        }

        /**
         * El intento falló por una causa ajena a las credenciales, como una caída de la base: no
         * cuenta, porque no se llegó a probar la contraseña.
         */
        public void discard() {
            attempt.discard();
        }
    }
}
