package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.domain.Household;
import io.github.patorinaldi.gastos.api.domain.User;
import io.github.patorinaldi.gastos.api.repository.HouseholdRepository;
import io.github.patorinaldi.gastos.api.repository.UserRepository;
import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    private final UserRepository users;
    private final HouseholdRepository households;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenIssuer tokenIssuer;

    // Hash contra el que se compara cuando el correo no existe. Sin esta comparación, la
    // respuesta llega antes para un correo sin cuenta (no corre bcrypt), y la diferencia de
    // tiempo alcanza para averiguar quién está registrado.
    private final String unknownUserHash;

    public AuthService(UserRepository users,
                       HouseholdRepository households,
                       PasswordEncoder passwordEncoder,
                       SessionTokenIssuer tokenIssuer) {
        this.users = users;
        this.households = households;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Valida las credenciales y emite el token de sesión.
     *
     * <p>El orden importa: primero la contraseña y recién después el estado de la cuenta. Al revés,
     * cualquiera podría averiguar si una cuenta está verificada sin conocer su contraseña.
     *
     * <p>A propósito no es {@code @Transactional}. Una transacción toma una conexión del pool al
     * empezar y la retiene hasta terminar, y bcrypt tarda del orden de 100 ms: muchos intentos de
     * login simultáneos, aun con correos inventados, agotarían el pool y dejarían sin conexión al
     * resto de la API. Sin transacción, cada consulta toma la conexión solo mientras corre, y
     * bcrypt no retiene ninguna. Ni {@code users} ni {@code households} tienen RLS, así que no
     * hace falta el hogar en contexto.
     */
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
        // El mismo chequeo que hace la sesión en cada petición: sin él, el login emitiría un token
        // que el resto de la API rechaza.
        boolean householdActive = households.findById(user.getHouseholdId())
                .map(Household::isActive)
                .orElse(false);
        if (!householdActive) {
            throw new HouseholdArchivedException();
        }

        return tokenIssuer.issue(user);
    }

    @Transactional(readOnly = true)
    public CurrentUser currentUser(AuthenticatedUser principal) {
        // La sesión ya comprobó que existen al autenticar la petición. Si dejaron de existir
        // entre ese momento y este, corresponde volver a iniciar sesión, no un error interno.
        User user = users.findById(principal.userId()).orElseThrow(InvalidSessionException::new);
        Household household = households.findById(principal.householdId())
                .orElseThrow(InvalidSessionException::new);
        return new CurrentUser(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.isEmailVerified(),
                household.getId(),
                household.getName());
    }
}