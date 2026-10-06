package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.config.JwtProperties;
import io.github.patorinaldi.gastos.api.domain.User;
import io.github.patorinaldi.gastos.api.security.SessionAuthenticationConverter;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Emite los tokens de sesión. Es el único lugar que arma uno: el login lo usa, y las pruebas
 * también, así que prueban exactamente los tokens que emite la aplicación.
 *
 * <p>Qué lleva el token:
 * <ul>
 *   <li>{@code sub}: el identificador del usuario. Es lo único que el servidor usa para saber quién
 *       hace la petición.</li>
 *   <li>{@value SessionAuthenticationConverter#SESSION_VERSION_CLAIM}: la versión de sesión del
 *       usuario al emitirlo. Si después cambia, por ejemplo al restablecer la contraseña, el token
 *       deja de valer: lo compara {@link SessionAuthenticationConverter} en cada petición.</li>
 *   <li>{@value #HOUSEHOLD_CLAIM}: el hogar al momento del login, como dato para el cliente
 *       (RF-05). El servidor no lo lee, y queda desactualizado si el usuario se cambia de hogar: el
 *       cliente tiene que tomar el hogar de {@code /api/auth/me} (RN-18).</li>
 * </ul>
 */
@Component
public class SessionTokenIssuer {

    public static final String HOUSEHOLD_CLAIM = "household";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public SessionTokenIssuer(JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /** Token para el usuario, emitido ahora. */
    public IssuedToken issue(User user) {
        return issue(user.getId(), user.getHouseholdId(), user.getSessionVersion(), clock.instant());
    }

    /**
     * Token emitido en un instante dado. Fuera del login sirve para las pruebas: un token emitido
     * hace más tiempo que la vigencia es un token vencido real.
     */
    public IssuedToken issue(UUID userId, UUID householdId, int sessionVersion, Instant issuedAt) {
        // Se trunca a segundos porque así guarda JWT los instantes: sin truncar, el vencimiento
        // que recibe el cliente no coincidiría con el del token.
        Instant issued = issuedAt.truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issued.plus(jwtProperties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim(SessionAuthenticationConverter.SESSION_VERSION_CLAIM, sessionVersion)
                .claim(HOUSEHOLD_CLAIM, householdId.toString())
                .issuedAt(issued)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}