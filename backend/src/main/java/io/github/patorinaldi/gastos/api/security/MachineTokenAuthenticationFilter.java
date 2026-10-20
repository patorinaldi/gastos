package io.github.patorinaldi.gastos.api.security;

import io.github.patorinaldi.gastos.api.repository.MachineTokenRepository;
import io.github.patorinaldi.gastos.api.service.auth.MachineTokenValues;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica a los clientes máquina, las automatizaciones que cargan gastos sin sesión (RF-31), por
 * el header {@code Authorization: Token <valor>}.
 *
 * <p>Solo actúa en los puntos de entrada de M6. En cualquier otra ruta ignora el header, como si
 * no hubiera venido: el token no habilita el resto de la API (HU-17), y esa petición termina en el
 * 401 de siempre por falta de sesión. Tampoco toca las peticiones con {@code Bearer}, que son del
 * filtro de la sesión.
 *
 * <p>Un token vale si existe, no está revocado y su hogar es el hogar actual de su dueño, que
 * además sigue activo (RN-18). Como con la sesión, el hogar de la petición sale de la base y no de
 * lo que trae el cliente. El token del hogar anterior ya se revoca al cambiarse de hogar; el
 * chequeo del hogar actual cubre que esa revocación, que es código de la aplicación, falle.
 *
 * <p>Todo rechazo es el mismo 401, sin importar el motivo, que queda solo en el log del servidor.
 * El log identifica al token por su id, nunca por su valor (RNF-09).
 *
 * <p>Si lo acepta, registra el uso en {@code last_used_at}, aunque la petición falle después: es
 * lo que se muestra para decidir qué token revocar, y lo que importa es que se usó.
 *
 * <p>No es un {@code @Component}, por lo mismo que {@link HouseholdContextFilter}.
 */
public class MachineTokenAuthenticationFilter extends OncePerRequestFilter {

    /** Autoridad de un cliente máquina, la que habilita los puntos de entrada de M6. */
    public static final String MACHINE_ROLE = "MACHINE";

    /** Esquema del header {@code Authorization}. */
    static final String SCHEME = "Token";

    private static final Logger log = LoggerFactory.getLogger(MachineTokenAuthenticationFilter.class);

    private static final List<GrantedAuthority> MACHINE_AUTHORITIES =
            AuthorityUtils.createAuthorityList("ROLE_" + MACHINE_ROLE);

    private final RequestMatcher endpoints;
    private final MachineTokenRepository tokens;
    private final MachineTokenValues values;
    private final AuthenticationEntryPoint entryPoint;
    private final Clock clock;

    public MachineTokenAuthenticationFilter(RequestMatcher endpoints,
                                            MachineTokenRepository tokens,
                                            MachineTokenValues values,
                                            AuthenticationEntryPoint entryPoint,
                                            Clock clock) {
        this.endpoints = endpoints;
        this.tokens = tokens;
        this.values = values;
        this.entryPoint = entryPoint;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<String> value = endpoints.matches(request) ? presentedToken(request) : Optional.empty();
        if (value.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<AuthenticatedUser> user = authenticate(value.get());
        if (user.isEmpty()) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, new BadCredentialsException("Token de cliente máquina rechazado"));
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.get(), null, MACHINE_AUTHORITIES));
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    // El esquema no distingue mayúsculas (RFC 9110, 11.1). Un header con otro esquema, como Bearer,
    // no es de este filtro.
    private static Optional<String> presentedToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, SCHEME + " ", 0, SCHEME.length() + 1)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(SCHEME.length() + 1).trim());
    }

    private Optional<AuthenticatedUser> authenticate(String value) {
        if (!values.isWellFormed(value)) {
            log.info("Token de cliente máquina rechazado: no tiene el formato de un token");
            return Optional.empty();
        }
        MachineTokenOwner owner = tokens.findOwner(values.hash(value)).orElse(null);
        if (owner == null) {
            log.info("Token de cliente máquina rechazado: no existe");
            return Optional.empty();
        }
        if (owner.revokedAt() != null) {
            return rejected(owner, "está revocado");
        }
        if (!owner.tokenHouseholdId().equals(owner.currentHouseholdId())) {
            return rejected(owner, "su dueño ya no integra el hogar del token");
        }
        if (!owner.householdActive()) {
            return rejected(owner, "el hogar está archivado");
        }

        tokens.markUsed(owner.tokenId(), clock.instant());
        return Optional.of(new AuthenticatedUser(owner.userId(), owner.tokenHouseholdId()));
    }

    private static Optional<AuthenticatedUser> rejected(MachineTokenOwner owner, String reason) {
        log.info("Token de cliente máquina {} rechazado: {}", owner.tokenId(), reason);
        return Optional.empty();
    }
}