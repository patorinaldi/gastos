package io.github.patorinaldi.gastos.api.service.auth;

import java.time.Instant;

/**
 * Token de sesión recién emitido y su vencimiento.
 *
 * <p>Es un tipo del servicio y no el {@code LoginResponse} de la API: el servicio no depende de la
 * capa web (RNF-22). El controlador lo traduce al contrato.
 */
public record IssuedToken(String token, Instant expiresAt) {
}
