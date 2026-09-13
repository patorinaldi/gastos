package io.github.patorinaldi.gastos.api.security;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Hogar del usuario en el hilo actual. Lo lee HouseholdTransactionListener
 * para fijarlo en Postgres al comenzar cada transacción.
 */
public final class CurrentHousehold {

    private static final ThreadLocal<UUID> HOUSEHOLD = new ThreadLocal<>();

    private CurrentHousehold() {
    }

    public static void set(UUID householdId) {
        HOUSEHOLD.set(Objects.requireNonNull(householdId, "householdId"));
    }

    public static Optional<UUID> get() {
        return Optional.ofNullable(HOUSEHOLD.get());
    }

    public static void clear() {
        HOUSEHOLD.remove();
    }
}