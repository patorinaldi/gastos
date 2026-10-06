package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.config.JwtProperties;
import io.github.patorinaldi.gastos.api.domain.Household;
import io.github.patorinaldi.gastos.api.domain.User;
import io.github.patorinaldi.gastos.api.repository.HouseholdRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicio de sesión (RF-04, RF-05) y datos del usuario autenticado.
 *
 * <p>Recibe y devuelve tipos propios, no los contratos de {@code web.dto}: las dependencias van de
 * la capa web al servicio y nunca al revés (RNF-22). La traducción la hace {@code AuthController}.
 */
@Service
public class AuthService {

    /** Claim con el hogar al emitir el token. Es para el cliente: el servidor no lo lee (RN-18). */
    public static final String HOUSEHOLD_CLAIM = "household";

    private final UserRepository users;
    private final HouseholdRepository households;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    // Hash contra el que se compara cuando el correo no existe. Sin esta comparación, la
    // respuesta llega antes para un correo sin cuenta (no corre bcrypt), y la diferencia de
    // tiempo alcanza para averiguar quién está registrado.
    private final String unknownUserHash;

    public AuthService(UserRepository users,
                       HouseholdRepository households,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       JwtProperties jwtProperties,
                       Clock clock) {
        this.users = users;
        this.households = households;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Valida las credenciales y emite el token de sesión.
     *
     * <p>El orden importa: primero la contraseña y recién después la verificación del correo.
     * Al revés, cualquiera podría averiguar si una cuenta está verificada sin conocer su
     * contraseña.
     */
    @Transactional(readOnly = true)
    public IssuedToken login(String email, String password) {
        // Una contraseña de más de 72 bytes no llega acá: LoginRequest la rechaza con 400 antes
        // de que bcrypt, que no admite más, responda con un error interno.
        User user = users.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, unknownUserHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }

        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public CurrentUser currentUser(AuthenticatedUser principal) {
        // El converter ya comprobó que ambos existen en esta misma petición.
        User user = users.findById(principal.userId()).orElseThrow();
        Household household = households.findById(principal.householdId()).orElseThrow();
        return new CurrentUser(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.isEmailVerified(),
                household.getId(),
                household.getName());
    }

    // El token lleva el usuario como subject y el hogar solo como dato para el cliente
    // (RF-05). Se trunca a segundos porque así guarda JWT los instantes: sin truncar, el
    // vencimiento que recibe el cliente no coincidiría con el del token.
    private IssuedToken issueToken(User user) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(jwtProperties.ttl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim(HOUSEHOLD_CLAIM, user.getHouseholdId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}