package io.github.patorinaldi.gastos.api;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base para las pruebas de endpoints que exigen sesión.
 *
 * <p>Los tokens se firman con el {@code JwtEncoder} de la aplicación, así que pasan por la misma
 * validación que uno emitido por el login. Uso típico:
 *
 * <pre>{@code
 * mockMvc.perform(get("/api/expenses").header(AUTHORIZATION, bearer(userId)))
 * }</pre>
 */
public abstract class AuthTestSupport extends HouseholdTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JwtEncoder jwtEncoder;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** Header {@code Authorization} con un token vigente para el usuario. */
    protected String bearer(UUID userId) {
        return "Bearer " + token(userId, Instant.now(), Duration.ofHours(1));
    }

    /**
     * Token firmado con la clave de la aplicación, emitido en {@code issuedAt} y con la vigencia
     * indicada. El hogar no se incluye: el servidor lo lee de la base.
     */
    protected String token(UUID userId, Instant issuedAt, Duration validity) {
        Instant issued = issuedAt.truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(issued)
                .expiresAt(issued.plus(validity))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
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
}