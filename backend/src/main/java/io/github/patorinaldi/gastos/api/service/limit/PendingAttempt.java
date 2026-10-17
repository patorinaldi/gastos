package io.github.patorinaldi.gastos.api.service.limit;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Un intento en curso, contado en uno o más {@link AttemptCounter}.
 *
 * <p>Cuenta como fallido desde que empieza, y deja de contar si se informa con {@link #discard()}.
 * Contarlo recién al fallar dejaría pasar de más a los intentos simultáneos: todos verían el
 * contador por debajo del tope mientras se resuelven. Si no se informa nada, queda como fallido.
 */
public final class PendingAttempt {

    private final List<Counted> counted;
    private boolean settled;

    private PendingAttempt(List<Counted> counted) {
        this.counted = counted;
    }

    /**
     * Registra un intento en cada contador, con su clave.
     *
     * @throws TooManyAttemptsException si alguno de los contadores llegó a su tope; el intento no
     *                                  se cuenta en ninguno
     */
    public static PendingAttempt start(AttemptCounter.Slot... slots) {
        // Primero se mira sin crear nada: un intento rechazado no agrega entradas, así que no
        // sirve para llenar los mapas y desplazar contadores reales. Tampoco suma en los demás
        // contadores: si sumara, insistir con una clave bloqueada terminaría bloqueando otras.
        Duration wait = Duration.ZERO;
        for (AttemptCounter.Slot slot : slots) {
            AttemptCounter.Window existing = slot.counter().peek(slot.key());
            if (existing != null && existing.isFull()) {
                wait = later(wait, existing.remaining(slot.counter().now()));
            }
        }
        if (!wait.isZero()) {
            throw new TooManyAttemptsException(wait);
        }

        List<Counted> counted = new ArrayList<>(slots.length);
        for (AttemptCounter.Slot slot : slots) {
            long now = slot.counter().now();
            AttemptCounter.Window window = slot.counter().open(slot.key(), now);
            int attempts = window.tryCount();
            if (attempts < 0) {
                // Otro intento simultáneo llenó este contador entre la mirada y el conteo.
                counted.forEach(c -> c.window().uncount());
                throw new TooManyAttemptsException(window.remaining(now));
            }
            if (attempts == slot.counter().max()) {
                slot.onLimitReached().run();
            }
            counted.add(new Counted(slot, window));
        }
        return new PendingAttempt(counted);
    }

    private static Duration later(Duration a, Duration b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    /** El intento no cuenta como fallido. Informarlo más de una vez no descuenta de nuevo. */
    public void discard() {
        settle(null);
    }

    /**
     * Como {@link #discard()}, y además {@code counter} vuelve a empezar de cero para la clave de
     * este intento. Los demás contadores no se reinician.
     */
    public void discardAndReset(AttemptCounter counter) {
        settle(counter);
    }

    private void settle(AttemptCounter reset) {
        if (settled) {
            return;
        }
        settled = true;
        for (Counted c : counted) {
            if (c.slot().counter() == reset) {
                reset.reset(c.slot().key(), c.window());
            } else {
                c.window().uncount();
            }
        }
    }

    private record Counted(AttemptCounter.Slot slot, AttemptCounter.Window window) {
    }
}
