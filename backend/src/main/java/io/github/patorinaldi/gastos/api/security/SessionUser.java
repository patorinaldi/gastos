package io.github.patorinaldi.gastos.api.security;

import java.util.UUID;

/**
 * Lo que hace falta leer de la base para autenticar una petición: el hogar actual del usuario, si
 * ese hogar sigue activo y la versión de sesión vigente. Lo devuelve
 * {@code UserRepository.findSessionUser}.
 */
public record SessionUser(UUID userId, UUID householdId, boolean householdActive, int sessionVersion) {
}