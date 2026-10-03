package io.github.patorinaldi.gastos.api.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Firma y vigencia del token de sesión.
 *
 * <p>La clave sale de {@code JWT_SECRET} y no tiene valor por defecto fuera del perfil dev: si
 * falta, la aplicación no arranca (RNF-07). El mínimo de 32 caracteres es el largo de una clave
 * HS256, que firma con HMAC-SHA256: una clave más corta se puede adivinar por fuerza bruta.
 *
 * @param secret clave de firma HS256
 * @param ttl    vigencia del token desde que se emite
 */
@Validated
@ConfigurationProperties("gastos.jwt")
public record JwtProperties(
        @NotBlank(message = "Falta gastos.jwt.secret (variable JWT_SECRET)")
        @Size(min = 32, message = "gastos.jwt.secret tiene que tener al menos 32 caracteres")
        String secret,
        @NotNull Duration ttl) {
}