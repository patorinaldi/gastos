package io.github.patorinaldi.gastos.api.security;

import io.github.patorinaldi.gastos.api.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * Convierte un token de sesión ya validado (firma y vencimiento) en el usuario autenticado de la
 * petición, con su hogar leído de la base.
 *
 * <p>El token solo aporta el identificador del usuario y su versión de sesión. El hogar puede
 * haber cambiado desde que se emitió, y un token firmado no se puede modificar, así que si el
 * hogar viniera de él, quien se cambió de hogar seguiría operando sobre el anterior hasta que el
 * token venza (RN-18). Cuesta una consulta por petición, que es lo que acepta M1 en
 * {@code modulos.md}, y esa misma consulta trae la versión de sesión: un token emitido antes de que
 * cambiara, por ejemplo antes de restablecer la contraseña, se rechaza.
 *
 * <p>Todo rechazo es un 401 con el mismo mensaje, sin importar el motivo: distinguir "el usuario
 * no existe" de "el hogar está archivado" le contaría el estado de una cuenta a quien tenga un
 * token viejo. Spring Security ya responde con una descripción genérica en
 * {@code WWW-Authenticate}; el mensaje único cuida que siga sin filtrarse aunque eso cambie. El
 * motivo real queda solo en el log del servidor.
 *
 * <p>No es un {@code @Component} a propósito: Spring Boot registraría cualquier bean
 * {@link Converter} también en la conversión de parámetros de Spring MVC.
 */
public class SessionAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Autoridad de la sesión web, la que habilita la API completa. */
    public static final String SESSION_ROLE = "SESSION";

    /** Claim con la versión de sesión. Lo emite {@code SessionTokenIssuer}. */
    public static final String SESSION_VERSION_CLAIM = "session_version";

    private static final String INVALID_SESSION = "La sesión no es válida";

    private static final Logger log = LoggerFactory.getLogger(SessionAuthenticationConverter.class);

    private static final List<GrantedAuthority> SESSION_AUTHORITIES =
            AuthorityUtils.createAuthorityList("ROLE_" + SESSION_ROLE);

    private final UserRepository users;

    public SessionAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = userId(jwt);
        SessionUser user = users.findSessionUser(userId).orElse(null);
        if (user == null) {
            throw rejected(userId, "el usuario no existe");
        }
        if (!user.householdActive()) {
            throw rejected(userId, "el hogar está archivado");
        }
        Number tokenVersion = jwt.getClaim(SESSION_VERSION_CLAIM);
        if (tokenVersion == null || tokenVersion.intValue() != user.sessionVersion()) {
            throw rejected(userId, "la versión de sesión del token no es la vigente");
        }

        AuthenticatedUser principal = new AuthenticatedUser(user.userId(), user.householdId());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, SESSION_AUTHORITIES);
    }

    // Con la firma válida, un subject que no es un UUID solo puede venir de quien tiene la
    // clave. Igual se responde 401 y no 500.
    private static UUID userId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException ex) {
            log.info("Sesión rechazada: el token no identifica a un usuario");
            throw new InvalidBearerTokenException(INVALID_SESSION);
        }
    }

    private static InvalidBearerTokenException rejected(UUID userId, String reason) {
        log.info("Sesión rechazada para el usuario {}: {}", userId, reason);
        return new InvalidBearerTokenException(INVALID_SESSION);
    }
}