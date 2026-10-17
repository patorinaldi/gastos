package io.github.patorinaldi.gastos.api.service.household;

import java.time.Instant;

/**
 * Invitación recién generada. Es el único lugar donde existe el código en claro: la base guarda su
 * hash. Ver {@link HouseholdView} sobre por qué el servicio devuelve tipos propios.
 */
public record IssuedInvitation(String code, Instant expiresAt) {
}
