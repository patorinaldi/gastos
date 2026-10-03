package io.github.patorinaldi.gastos.api.security;

import io.github.patorinaldi.gastos.api.repository.UserRepository;
import java.util.List;
import java.util.UUID;
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
 * <p>El token solo aporta el identificador del usuario. El hogar puede haber cambiado desde que
 * se emitió, y un token firmado no se puede modificar ni revocar, así que si el hogar viniera de
 * él, quien se cambió de hogar seguiría operando sobre el anterior hasta que el token venza
 * (RN-18). Cuesta una consulta por petición, que es lo que acepta M1 en {@code modulos.md}.
 *
 * <p>Las excepciones son {@link InvalidBearerTokenException} para que la cadena responda 401: un
 * token que no corresponde a un usuario con hogar activo no sirve para nada, aunque la firma sea
 * válida. Un hogar archivado no debería alcanzarse con una sesión, porque se archiva cuando lo
 * deja su último integrante; el chequeo es defensivo.
 *
 * <p>No es un {@code @Component} a propósito: Spring Boot registraría cualquier bean
 * {@link Converter} también en la conversión de parámetros de Spring MVC.
 */
public class SessionAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Autoridad de la sesión web, la que habilita la API completa. */
    public static final String SESSION_ROLE = "SESSION";

    private static final List<GrantedAuthority> SESSION_AUTHORITIES =
            AuthorityUtils.createAuthorityList("ROLE_" + SESSION_ROLE);

    private final UserRepository users;

    public SessionAuthenticationConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        SessionUser user = users.findSessionUser(userId(jwt))
                .orElseThrow(() -> new InvalidBearerTokenException("El usuario de la sesión no existe"));
        if (!user.householdActive()) {
            throw new InvalidBearerTokenException("El hogar de la sesión está archivado");
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
            throw new InvalidBearerTokenException("El token no identifica a un usuario");
        }
    }
}