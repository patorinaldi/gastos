package io.github.patorinaldi.gastos.api.service.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import io.github.patorinaldi.gastos.api.config.LoginLimitProperties;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
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
 * correcta, o si falla por otra causa que no son las credenciales. Contarlo recién al fallar
 * dejaría pasar de más a los intentos simultáneos: todos verían el contador por debajo del tope
 * mientras bcrypt compara.
 *
 * <p>Los contadores viven en memoria: alcanza con una sola instancia de la API, y un reinicio los
 * pone en cero. Con más de una instancia, cada una contaría por su lado.
 */
@Component
public class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    // Tope de contadores en memoria. Sin él, un ataque con muchos correos o direcciones haría
    // crecer los mapas sin límite. Superado, Caffeine descarta los menos usados.
    private static final long MAX_TRACKED_KEYS = 100_000;

    // Bytes que se conservan de una IPv6: los primeros 64 bits, el prefijo que suele recibir
    // entero un solo cliente.
    private static final int IPV6_PREFIX_BYTES = 8;

    private final LoginLimitProperties limits;
    private final Ticker ticker;
    private final Cache<String, Window> byEmailAndIp;
    private final Cache<String, Window> byIp;
    private final Cache<String, Window> byEmail;

    @Autowired
    public LoginAttemptLimiter(LoginLimitProperties limits) {
        this(limits, Ticker.systemTicker());
    }

    LoginAttemptLimiter(LoginLimitProperties limits, Ticker ticker) {
        this.limits = limits;
        this.ticker = ticker;
        this.byEmailAndIp = newCache();
        this.byIp = newCache();
        this.byEmail = newCache();
    }

    // expireAfterWrite cuenta desde que se crea la entrada: los intentos incrementan el contador
    // sin volver a escribirla, así que la ventana no se corre con cada intento.
    private Cache<String, Window> newCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(limits.window())
                .maximumSize(MAX_TRACKED_KEYS)
                .ticker(ticker)
                .build();
    }

    /**
     * Registra un intento de inicio de sesión.
     *
     * @param ip dirección del cliente, sin puerto
     * @throws LoginBlockedException si alguno de los contadores llegó a su tope
     */
    public Attempt start(String email, String ip) {
        long now = ticker.read();
        // Mayúsculas, como compara la base (findByEmailIgnoreCase usa upper): en minúsculas,
        // variantes como "ſofia" o "sofıa" encontrarían la misma cuenta con otro contador.
        String emailKey = email.toUpperCase(Locale.ROOT);
        String ipKey = addressKey(ip);
        String pairKey = emailKey + " " + ipKey;

        // Primero se mira sin crear nada: un intento rechazado no agrega entradas, así que no
        // sirve para llenar los mapas y desplazar contadores reales.
        Limit[] counters = {
                new Limit(LimitKind.EMAIL_AND_IP, byEmailAndIp, pairKey, limits.maxPerEmailAndIp()),
                new Limit(LimitKind.IP, byIp, ipKey, limits.maxPerIp()),
                new Limit(LimitKind.EMAIL, byEmail, emailKey, limits.maxPerEmail()),
        };
        Duration wait = Duration.ZERO;
        for (Limit limit : counters) {
            Window existing = limit.cache().getIfPresent(limit.key());
            if (existing != null && existing.isFull(limit.max())) {
                wait = later(wait, existing.remaining(now));
            }
        }
        if (!wait.isZero()) {
            throw new LoginBlockedException(wait);
        }

        Window[] counted = new Window[counters.length];
        for (int i = 0; i < counters.length; i++) {
            Limit limit = counters[i];
            Window window = limit.cache().get(limit.key(), k -> new Window(now));
            int attempts = window.tryCount(limit.max());
            if (attempts < 0) {
                // Otro intento simultáneo llenó este contador entre la mirada y el conteo.
                for (int j = 0; j < i; j++) {
                    counted[j].uncount();
                }
                throw new LoginBlockedException(window.remaining(now));
            }
            if (attempts == limit.max()) {
                logLimitReached(limit.kind(), emailKey, ipKey);
            }
            counted[i] = window;
        }
        return new Attempt(pairKey, counted[0], counted[1], counted[2]);
    }

    private static Duration later(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /**
     * Clave de la dirección. Una IPv6 se agrupa por su prefijo /64: un solo cliente suele tener
     * el prefijo entero, y con cada dirección por separado podría rotar entre miles de millones.
     */
    static String addressKey(String ip) {
        // Solo una IPv6 tiene ":". El chequeo evita además que getByName resuelva por DNS algo
        // que no sea una dirección literal.
        if (!ip.contains(":")) {
            return ip;
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (!(address instanceof Inet6Address)) {
                // Una IPv4 escrita como IPv6 (::ffff:203.0.113.7) cuenta como la IPv4.
                return address.getHostAddress();
            }
            byte[] bytes = Arrays.copyOf(address.getAddress(), 16);
            Arrays.fill(bytes, IPV6_PREFIX_BYTES, 16, (byte) 0);
            return InetAddress.getByAddress(bytes).getHostAddress() + "/64";
        } catch (UnknownHostException e) {
            return ip;
        }
    }

    // Se registra una vez por ventana, al llegar al tope, y no en cada rechazo: un ataque no
    // inunda el log. El correo va como hash (RNF-09), que alcanza para ver si los bloqueos
    // repiten cuenta.
    private void logLimitReached(LimitKind kind, String emailKey, String ipKey) {
        switch (kind) {
            case EMAIL_AND_IP -> log.warn("Límite de intentos de login por correo y dirección: correo={} ip={}",
                    hash(emailKey), ipKey);
            case IP -> log.warn("Límite de intentos de login por dirección: ip={}", ipKey);
            case EMAIL -> log.warn("Límite de intentos de login por correo, desde cualquier dirección: correo={}",
                    hash(emailKey));
        }
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

        private final String pairKey;
        private final Window pair;
        private final Window address;
        private final Window email;
        private boolean settled;

        private Attempt(String pairKey, Window pair, Window address, Window email) {
            this.pairKey = pairKey;
            this.pair = pair;
            this.address = address;
            this.email = email;
        }

        /**
         * La contraseña era correcta: el correo vuelve a empezar de cero desde esa dirección (CU-02,
         * paso 5), y el intento deja de contar como fallido en los otros dos contadores, que no se
         * reinician. Si se reiniciara el de la dirección, quien tenga una cuenta propia podría
         * reiniciarlo entre intentos contra otras.
         */
        public void succeeded() {
            if (settled) {
                return;
            }
            settled = true;
            byEmailAndIp.asMap().remove(pairKey, pair);
            address.uncount();
            email.uncount();
        }

        /**
         * El intento falló por una causa ajena a las credenciales, como una caída de la base: no
         * cuenta, porque no se llegó a probar la contraseña.
         */
        public void discard() {
            if (settled) {
                return;
            }
            settled = true;
            pair.uncount();
            address.uncount();
            email.uncount();
        }
    }

    private enum LimitKind { EMAIL_AND_IP, IP, EMAIL }

    private record Limit(LimitKind kind, Cache<String, Window> cache, String key, int max) {
    }

    private final class Window {

        private final long startedAt;
        private final AtomicInteger attempts = new AtomicInteger();

        Window(long startedAt) {
            this.startedAt = startedAt;
        }

        /**
         * Suma el intento solo si no llegó al tope, y devuelve el nuevo total, o -1 si ya estaba
         * en el tope. Comparar y sumar en un solo paso evita que dos intentos simultáneos pasen
         * los dos con el contador en el tope menos uno.
         */
        int tryCount(int max) {
            int current;
            do {
                current = attempts.get();
                if (current >= max) {
                    return -1;
                }
            } while (!attempts.compareAndSet(current, current + 1));
            return current + 1;
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
