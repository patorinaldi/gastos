package io.github.patorinaldi.gastos.api.security;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que hace falta leer de la base para autenticar un token de cliente máquina: el hogar del
 * token, si está revocado, y el hogar actual de su dueño y si sigue activo. Lo devuelve
 * {@code MachineTokenRepository.findOwner}.
 */
public record MachineTokenOwner(
        UUID tokenId,
        UUID userId,
        UUID tokenHouseholdId,
        Instant revokedAt,
        UUID currentHouseholdId,
        boolean householdActive) {
}