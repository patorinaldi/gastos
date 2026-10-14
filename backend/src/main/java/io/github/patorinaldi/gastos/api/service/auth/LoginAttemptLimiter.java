package io.github.patorinaldi.gastos.api.service.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.patorinaldi.gastos.api.config.LoginLimitProperties;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Límite de intentos fallidos de inicio de sesión (RNF-06), con dos contadores:
 *
 * <ul>
 *   <li>por correo y dirección IP: frena a quien prueba contraseñas sobre una cuenta, sin que un
 *       tercero pueda bloquearle el acceso al dueño desde otra dirección;</li>
 *   <li>por dirección IP, con un tope mayor: frena a quien prueba muchos correos desde un mismo
 *       lugar.</li>
 * </ul>
 *
 * <p>Cada contador es una ventana fija que empieza con el primer intento y dura
 * {@link LoginLimitProperties#window()}. Superado el tope, los intentos se rechazan hasta que la
 * ventana cierra, sin comparar la contraseña.
 *
 * <p>Un intento cuenta como fallido desde que empieza y deja de contar si la contraseña resulta
 * correcta. Contarlo recién al fallar dejaría pasar de más a los intentos simultáneos: todos
 * verían el contador por debajo del tope mientras bcrypt compara.
 *
 * <p>Los contadores viven en memoria: alcanza con una sola instancia de la API, y un reinicio los
 * pone en cero. Con más de una instancia, cada una contaría por su lado.
 */
@Component
public class LoginAttemptLimiter {

    // Tope de contadores en memoria. Sin él, un ataque con muchos correos o direcciones haría
    // crecer los mapas sin límite. Superado, Caffeine descarta los menos usados.
    private static final long MAX_TRACKED_KEYS = 100_000;

    private final LoginLimitProperties limits;
    private final Ticker ticker;
    private final Cache<EmailAndIp, Window> byEmailAndIp;
    private final Cache<String, Window> byIp;

    @Autowired
    public LoginAttemptLimiter(LoginLimitProperties limits) {
        this(limits, Ticker.systemTicker());
    }

    LoginAttemptLimiter(LoginLimitProperties limits, Ticker ticker) {
        this.limits = limits;
        this.ticker = ticker;
        this.byEmailAndIp = newCache();
        this.byIp = newCache();
    }

    // expireAfterWrite cuenta desde que se crea la entrada: los intentos incrementan el contador
    // sin volver a escribirla, así que la ventana no se corre con cada intento.
    private <K> Cache<K, Window> newCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(limits.window())
                .maximumSize(MAX_TRACKED_KEYS)
                .ticker(ticker)
                .build();
    }

    /**
     * Registra un intento de inicio de sesión.
     *
     * @throws LoginBlockedException si alguno de los dos contadores llegó a su tope
     */
    public Attempt start(String email, String ip) {
        long now = ticker.read();
        EmailAndIp key = new EmailAndIp(email.toLowerCase(Locale.ROOT), ip);
        Window pair = byEmailAndIp.get(key, k -> new Window(now));
        Window address = byIp.get(ip, k -> new Window(now));

        if (!address.tryCount(limits.maxPerIp())) {
            throw blocked(now, pair, address);
        }
        if (!pair.tryCount(limits.maxPerEmailAndIp())) {
            address.uncount();
            throw blocked(now, pair, address);
        }
        return new Attempt(key, pair, address);
    }

    // Si los dos contadores están en el tope, hay que esperar a que cierre el último.
    private LoginBlockedException blocked(long now, Window pair, Window address) {
        Duration wait = Duration.ZERO;
        if (pair.isFull(limits.maxPerEmailAndIp())) {
            wait = pair.remaining(now);
        }
        if (address.isFull(limits.maxPerIp())) {
            Duration addressWait = address.remaining(now);
            wait = addressWait.compareTo(wait) > 0 ? addressWait : wait;
        }
        return new LoginBlockedException(wait);
    }

    /** Un intento en curso. Si la contraseña resulta correcta, se informa con {@link #succeeded()}. */
    public final class Attempt {

        private final EmailAndIp key;
        private final Window pair;
        private final Window address;

        private Attempt(EmailAndIp key, Window pair, Window address) {
            this.key = key;
            this.pair = pair;
            this.address = address;
        }

        /**
         * La contraseña era correcta: el correo vuelve a empezar de cero desde esa dirección (CU-02,
         * paso 7), y el intento deja de contar como fallido para la dirección. El contador de la
         * dirección no se reinicia: si no, quien tenga una cuenta propia podría reiniciarlo entre
         * intentos contra otras.
         */
        public void succeeded() {
            byEmailAndIp.asMap().remove(key, pair);
            address.uncount();
        }
    }

    private record EmailAndIp(String email, String ip) {
    }

    private final class Window {

        private final long startedAt;
        private final AtomicInteger attempts = new AtomicInteger();

        Window(long startedAt) {
            this.startedAt = startedAt;
        }

        // Suma el intento solo si no llegó al tope. Comparar y sumar en un solo paso evita que dos
        // intentos simultáneos pasen los dos con el contador en el tope menos uno.
        boolean tryCount(int max) {
            int current;
            do {
                current = attempts.get();
                if (current >= max) {
                    return false;
                }
            } while (!attempts.compareAndSet(current, current + 1));
            return true;
        }

        void uncount() {
            attempts.decrementAndGet();
        }

        boolean isFull(int max) {
            return attempts.get() >= max;
        }

        Duration remaining(long now) {
            Duration elapsed = Duration.ofNanos(now - startedAt);
            Duration left = limits.window().minus(elapsed);
            return left.isNegative() ? Duration.ZERO : left;
        }
    }
}