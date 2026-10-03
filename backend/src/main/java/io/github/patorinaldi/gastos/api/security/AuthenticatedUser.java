package io.github.patorinaldi.gastos.api.security;

import java.util.UUID;

/**
 * Quién hace la petición y a qué hogar accede. Es el principal de toda petición autenticada, y
 * los controladores lo reciben con {@code @AuthenticationPrincipal}.
 *
 * <p>El hogar no sale del token sino de {@code users.household_id}, leído en cada petición
 * (RN-18). Por eso es el único dato de hogar en el que puede confiar el código de la aplicación:
 * el responsable de un gasto, el hogar de una invitación o el de un listado salen de acá, nunca
 * del cuerpo ni de la ruta.
 */
public record AuthenticatedUser(UUID userId, UUID householdId) {
}