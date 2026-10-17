package io.github.patorinaldi.gastos.api.service.limit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contador de intentos fallidos por clave, en ventanas fijas: cada clave cuenta desde su primer
 * intento y durante {@code window}, hasta {@code max}. Superado el tope, los intentos con esa
 * clave se rechazan hasta que la ventana cierra.
 *
 * <p>No registra intentos por sí solo: los registra {@link PendingAttempt#start}, que cuenta en
 * varios contadores a la vez.
 *
 * <p>Vive en memoria: alcanza con una sola instancia de la API, y un reinicio lo pone en cero. Con
 * más de una instancia, cada una contaría por su lado.
 */
public final class AttemptCounter {

    // Tope de claves en memoria. Sin él, un ataque con muchas claves distintas haría crecer el
    // mapa sin límite. Superado, Caffeine descarta las menos usadas.
    private static final long MAX_TRACKED_KEYS = 100_000;

    private final Duration window;
    private final int max;
    private final Ticker ticker;
    private final Cache<String, Window> windows;

    public AttemptCounter(Duration window, int max, Ticker ticker) {
        this.window = window;
        this.max = max;
        this.ticker = ticker;
        // expireAfterWrite cuenta desde que se crea la entrada: los intentos incrementan el
        // contador sin volver a escribirla, así que la ventana no se corre con cada intento.
        this.windows = Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(MAX_TRACKED_KEYS)
                .ticker(ticker)
                .build();
    }

    /**
     * La clave {@code key} de este contador, para {@link PendingAttempt#start}.
     *
     * @param onLimitReached corre cuando un intento llena la ventana: una vez por ventana, y no en
     *                       cada rechazo, así que un ataque no inunda el log
     */
    public Slot slot(String key, Runnable onLimitReached) {
        return new Slot(this, key, onLimitReached);
    }

    public record Slot(AttemptCounter counter, String key, Runnable onLimitReached) {
    }

    int max() {
        return max;
    }

    long now() {
        return ticker.read();
    }

    /** La ventana de la clave, sin crearla si no existe. */
    Window peek(String key) {
        return windows.getIfPresent(key);
    }

    Window open(String key, long now) {
        return windows.get(key, k -> new Window(now));
    }

    /** Descarta la ventana, salvo que ya la haya reemplazado otra. */
    void reset(String key, Window existing) {
        windows.asMap().remove(key, existing);
    }

    final class Window {

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
        int tryCount() {
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

        boolean isFull() {
            return attempts.get() >= max;
        }

        Duration remaining(long now) {
            Duration elapsed = Duration.ofNanos(now - startedAt);
            Duration left = window.minus(elapsed);
            return left.isNegative() ? Duration.ZERO : left;
        }
    }
}
