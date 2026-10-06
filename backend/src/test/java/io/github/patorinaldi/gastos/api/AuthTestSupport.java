package io.github.patorinaldi.gastos.api;

import io.github.patorinaldi.gastos.api.service.auth.SessionTokenIssuer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base para las pruebas de endpoints que exigen sesión.
 *
 * <p>Los tokens los emite {@link SessionTokenIssuer}, el mismo componente que usa el login, así que
 * son exactamente los que emite la aplicación. Uso típico:
 *
 * <pre>{@code
 * mockMvc.perform(get("/api/expenses").header(AUTHORIZATION, bearer(userId)))
 * }</pre>
 */
public abstract class AuthTestSupport extends HouseholdTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected SessionTokenIssuer tokenIssuer;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** Header {@code Authorization} con un token vigente para el usuario. */
    protected String bearer(UUID userId) {
        return "Bearer " + token(userId, Instant.now());
    }

    /**
     * Token del usuario emitido en {@code issuedAt}, con su hogar y su versión de sesión actuales.
     * Emitido hace más tiempo que la vigencia, es un token vencido real.
     *
     * <p>Si el usuario no existe, lleva un hogar cualquiera y la versión 0: sirve para probar que
     * la sesión rechaza a un usuario que no está en la base.
     */
    protected String token(UUID userId, Instant issuedAt) {
        List<Session> current = jdbcTemplate.query(
                "select household_id, session_version from users where id = ?",
                (rs, row) -> new Session(rs.getObject("household_id", UUID.class), rs.getInt("session_version")),
                userId);
        Session session = current.isEmpty() ? new Session(UUID.randomUUID(), 0) : current.getFirst();
        return tokenIssuer.issue(userId, session.householdId(), session.version(), issuedAt).token();
    }

    /** Usuario que puede iniciar sesión: contraseña real con bcrypt y correo verificado. */
    protected UUID createVerifiedUser(UUID household, String email, String name, String password) {
        UUID user = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, household_id, email, password_hash, name, email_verified)"
                        + " values (?, ?, ?, ?, ?, true)",
                user, household, email, passwordEncoder.encode(password), name);
        return user;
    }

    /** Correo único por ejecución, para no chocar con users_email_key si una limpieza falla. */
    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@ejemplo.test";
    }

    private record Session(UUID householdId, int version) {
    }
}