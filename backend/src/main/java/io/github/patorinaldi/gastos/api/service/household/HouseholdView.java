package io.github.patorinaldi.gastos.api.service.household;

import java.time.Instant;
import java.util.UUID;

/**
 * Hogar tal como lo devuelve el servicio. El controlador lo traduce a {@code HouseholdResponse}: el
 * servicio no conoce {@code web.dto} (RNF-22).
 */
public record HouseholdView(UUID id, String name, Instant createdAt) {
}
