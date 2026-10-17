package io.github.patorinaldi.gastos.api.web;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invitaciones a un hogar: generación del código (HU-05; RF-08, RN-05).
 */
class InvitationTest extends AuthTestSupport {

    private UUID casa;
    private UUID ana;

    @BeforeEach
    void sembrarHogar() {
        casa = createHousehold("Casa de Ana");
        ana = createUser(casa, uniqueEmail("ana"), "Ana");
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
    }

    @Test
    void generarDevuelveUnCodigoQueVenceEnSieteDias() throws Exception {
        Instant antes = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        String body = generar(ana)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Instant despues = Instant.now();
        assertThat((String) JsonPath.read(body, "$.code")).matches("GST-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{3}");
        Instant vence = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(vence).isBetween(antes.plus(Duration.ofDays(7)), despues.plus(Duration.ofDays(7)));
    }

    // La base no guarda el código: solo su SHA-256, el mismo vencimiento que se respondió, y quién
    // la generó para qué hogar.
    @Test
    void laBaseGuardaSoloElHashDelCodigo() throws Exception {
        String body = generar(ana).andReturn().getResponse().getContentAsString();
        String codigo = JsonPath.read(body, "$.code");

        List<Map<String, Object>> filas = jdbcTemplate.queryForList(
                "select household_id, created_by, code_hash, expires_at, redeemed_by, redeemed_at"
                        + " from household_invitations where household_id = ?", casa);

        assertThat(filas).hasSize(1);
        Map<String, Object> fila = filas.getFirst();
        assertThat(fila.get("household_id")).isEqualTo(casa);
        assertThat(fila.get("created_by")).isEqualTo(ana);
        assertThat((byte[]) fila.get("code_hash")).isEqualTo(sha256(codigo.replace("-", "")));
        assertThat(((java.sql.Timestamp) fila.get("expires_at")).toInstant())
                .isEqualTo(Instant.parse(JsonPath.read(body, "$.expiresAt")));
        assertThat(fila.get("redeemed_by")).isNull();
        assertThat(fila.get("redeemed_at")).isNull();
    }

    @Test
    void cadaInvitacionTieneSuPropioCodigo() throws Exception {
        String primera = generar(ana).andReturn().getResponse().getContentAsString();
        String segunda = generar(ana).andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(primera, "$.code")).isNotEqualTo(JsonPath.read(segunda, "$.code"));
    }

    @Test
    void sinSesionNoGeneraNada() throws Exception {
        mockMvc.perform(post("/api/household/invitations"))
                .andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from household_invitations where household_id = ?", Integer.class, casa))
                .isZero();
    }

    private ResultActions generar(UUID usuario) throws Exception {
        return mockMvc.perform(post("/api/household/invitations").header(AUTHORIZATION, bearer(usuario)));
    }

    private static byte[] sha256(String texto) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.US_ASCII));
    }
}
