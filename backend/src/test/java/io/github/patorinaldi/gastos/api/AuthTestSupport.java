package io.github.patorinaldi.gastos.api;

import io.github.patorinaldi.gastos.api.service.auth.SessionTokenIssuer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

    private static final AtomicInteger NEXT_LOGIN_ADDRESS = new AtomicInteger();

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

    /**
     * {@code POST /api/auth/login} desde una dirección distinta en cada llamada.
     *
     * <p>El límite de intentos guarda sus contadores en memoria, y el contexto de Spring se
     * comparte entre clases de prueba. Desde una dirección fija, los fallos de todas las pruebas se
     * sumarían y, pasado el tope por dirección, empezarían a aparecer 429 según el orden en que
     * corran. Las pruebas del límite en sí fijan la dirección a mano.
     */
    protected MockHttpServletRequestBuilder loginRequest(String email, String password) {
        int n = NEXT_LOGIN_ADDRESS.incrementAndGet();
        String address = "10." + (n >> 16 & 0xff) + "." + (n >> 8 & 0xff) + "." + (n & 0xff);
        return post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, password));
    }

    protected ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(loginRequest(email, password));
    }

    /** Correo único por ejecución, para no chocar con users_email_key si una limpieza falla. */
    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@ejemplo.test";
    }

    private record Session(UUID householdId, int version) {
    }
}