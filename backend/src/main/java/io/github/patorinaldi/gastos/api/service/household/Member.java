package io.github.patorinaldi.gastos.api.service.household;

import java.util.UUID;

/** Integrante del hogar, tal como lo devuelve el servicio. Ver {@link HouseholdView}. */
public record Member(UUID id, String name, String email) {
}
