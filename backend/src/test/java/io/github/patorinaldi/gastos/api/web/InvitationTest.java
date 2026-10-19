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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invitaciones a un hogar: generación del código (HU-05; RF-08, RN-05, RNF-30).
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

    // ── Tope de invitaciones vigentes (RNF-30) ─────────────────────────────────────────────

    @Test
    void conDiezVigentesNoGeneraOtra() throws Exception {
        generarVigentes(10);

        generar(ana)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(
                        "El hogar ya tiene 10 invitaciones vigentes. Podés generar otra cuando alguna se use o venza."));

        assertThat(invitaciones()).isEqualTo(10);
    }

    // El tope es del hogar: otro integrante tampoco puede pasarlo.
    @Test
    void elTopeEsDelHogarYNoDeQuienLasGenera() throws Exception {
        generarVigentes(10);
        UUID bruno = createUser(casa, uniqueEmail("bruno"), "Bruno");

        generar(bruno).andExpect(status().isConflict());
    }

    @Test
    void unaInvitacionVencidaDejaDeContar() throws Exception {
        generarVigentes(10);
        jdbcTemplate.update("update household_invitations set expires_at = now() - interval '1 second'"
                + " where id = (select id from household_invitations where household_id = ? limit 1)", casa);

        generar(ana).andExpect(status().isCreated());
    }

    @Test
    void unaInvitacionCanjeadaDejaDeContar() throws Exception {
        generarVigentes(10);
        UUID otraCasa = createHousehold("Casa de Carla");
        UUID carla = createUser(otraCasa, uniqueEmail("carla"), "Carla");
        try {
            jdbcTemplate.update("update household_invitations set redeemed_by = ?, redeemed_at = now()"
                    + " where id = (select id from household_invitations where household_id = ? limit 1)",
                    carla, casa);

            generar(ana).andExpect(status().isCreated());
        } finally {
            // También borra la invitación que canjeó Carla: su clave foránea impediría borrarla.
            deleteHousehold(otraCasa);
        }
    }

    private void generarVigentes(int cantidad) throws Exception {
        for (int i = 0; i < cantidad; i++) {
            generar(ana).andExpect(status().isCreated());
        }
    }

    private int invitaciones() {
        return jdbcTemplate.queryForObject(
                "select count(*) from household_invitations where household_id = ?", Integer.class, casa);
    }

    private ResultActions generar(UUID usuario) throws Exception {
        return mockMvc.perform(post("/api/household/invitations").header(AUTHORIZATION, bearer(usuario)));
    }

    private static byte[] sha256(String texto) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.US_ASCII));
    }
}
