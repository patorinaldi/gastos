package io.github.patorinaldi.gastos.api.service.auth;

import java.time.Instant;
import java.util.UUID;

/** Un token de cliente máquina en el listado: sin su valor ni su hash. */
public record MachineTokenView(UUID id, String name, Instant createdAt, Instant lastUsedAt, Instant revokedAt) {
}
