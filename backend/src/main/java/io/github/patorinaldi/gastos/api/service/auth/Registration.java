package io.github.patorinaldi.gastos.api.service.auth;

import java.util.UUID;

/**
 * Resultado del alta: la cuenta, su hogar recién creado y el estado del correo, que siempre nace
 * sin verificar (RN-02).
 */
public record Registration(UUID userId, UUID householdId, boolean emailVerified) {
}
