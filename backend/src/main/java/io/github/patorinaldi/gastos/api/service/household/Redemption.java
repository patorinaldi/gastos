package io.github.patorinaldi.gastos.api.service.household;

import java.util.UUID;

/** Resultado de un canje aplicado. Ver {@link HouseholdView}. */
public record Redemption(UUID householdId, String householdName, boolean previousHouseholdArchived) {
}
