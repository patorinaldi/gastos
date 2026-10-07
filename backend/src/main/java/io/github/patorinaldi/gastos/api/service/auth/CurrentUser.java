package io.github.patorinaldi.gastos.api.service.auth;

import java.util.UUID;

/**
 * Usuario autenticado y su hogar, tal como los devuelve el servicio. El controlador lo traduce a
 * {@code CurrentUserResponse}, por el mismo motivo que {@link IssuedToken}.
 */
public record CurrentUser(
        UUID userId,
        String name,
        String email,
        boolean emailVerified,
        UUID householdId,
        String householdName) {
}
