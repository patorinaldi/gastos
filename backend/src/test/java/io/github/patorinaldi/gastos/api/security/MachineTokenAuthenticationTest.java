package io.github.patorinaldi.gastos.api.security;

import io.github.patorinaldi.gastos.api.AuthTestSupport;
import io.github.patorinaldi.gastos.api.domain.Category;
import io.github.patorinaldi.gastos.api.repository.CategoryRepository;
import io.github.patorinaldi.gastos.api.repository.MachineTokenRepository;
import io.github.patorinaldi.gastos.api.service.auth.MachineTokenValues;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El canal de los clientes máquina de punta a punta: del header {@code Authorization: Token} al
 * hogar que ven las políticas RLS. Cubre lo que resuelve {@link MachineTokenAuthenticationFilter}.
 *
 * <p>Como el punto de entrada de captura todavía no existe, se prueba con {@link CaptureProbe}, un
 * controlador que solo existe en esta prueba, bajo {@code /api/capture}: devuelve a quién autenticó
 * la petición y las categorías que ve a través del repositorio, igual que hará M6. Los tokens se
 * insertan directo en la base con {@link MachineTokenValues}, que es lo que usará la emisión.
 */
class MachineTokenAuthenticationTest extends AuthTestSupport {

    @Autowired
    private MachineTokenValues values;

    @Autowired
    private MachineTokenRepository machineTokens;

    private UUID casaA;
    private UUID casaB;
    private UUID ana;
    private UUID beto;

    @BeforeEach
    void sembrarDosHogares() {
        casaA = createHousehold("Casa A");
        casaB = createHousehold("Casa B");
        ana = createUser(casaA, uniqueEmail("ana"), "Ana");
        beto = createUser(casaB, uniqueEmail("beto"), "Beto");
        withHousehold(casaA, () -> jdbcTemplate.update(
                "insert into categories (household_id, name) values (?, ?)", casaA, "Solo de A"));
        withHousehold(casaB, () -> jdbcTemplate.update(
                "insert into categories (household_id, name) values (?, ?)", casaB, "Solo de B"));
    }

    // Si Ana se cambió a B, su token en A se borra con A, y a ella con B.
    @AfterEach
    void limpiar() {
        deleteHousehold(casaA);
        deleteHousehold(casaB);
    }

    @Test
    void conUnTokenValidoOperaComoSuDuenoEnSuHogar() throws Exception {
        String deAna = emitir(ana, casaA);
        String deBeto = emitir(beto, casaB);

        sonda(deAna)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ana.toString()))
                .andExpect(jsonPath("$.householdId").value(casaA.toString()))
                .andExpect(jsonPath("$.categories", contains("Solo de A")));
        sonda(deBeto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId").value(casaB.toString()))
                .andExpect(jsonPath("$.categories", contains("Solo de B")));
    }

    @Test
    void registraElUltimoUso() throws Exception {
        String token = emitir(ana, casaA);
        Instant antes = Instant.now();

        sonda(token).andExpect(status().isOk());

        assertThat(ultimoUso(token)).isAfterOrEqualTo(antes.minusSeconds(1));
    }

    // RFC 9110: el esquema no distingue mayúsculas, y algunos clientes lo mandan en minúsculas.
    @Test
    void elEsquemaNoDistingueMayusculas() throws Exception {
        String token = emitir(ana, casaA);

        mockMvc.perform(get(CaptureProbe.PATH).header(AUTHORIZATION, "token " + token))
                .andExpect(status().isOk());
    }

    @Test
    void unTokenQueNoExisteSeRechaza() throws Exception {
        rechazado(sonda(values.generate()));
    }

    @Test
    void unValorSinElFormatoDeUnTokenSeRechaza() throws Exception {
        rechazado(sonda("no-es-un-token"));
        rechazado(sonda(""));
        rechazado(sonda(emitir(ana, casaA) + "x"));
    }

    @Test
    void unTokenRevocadoSeRechaza() throws Exception {
        String token = emitir(ana, casaA);
        jdbcTemplate.update("update machine_tokens set revoked_at = now() where token_hash = ?", values.hash(token));

        rechazado(sonda(token));
        assertThat(ultimoUso(token)).isNull();
    }

    // RN-18: si la revocación al cambiarse de hogar fallara, el token igual deja de valer, porque
    // su hogar ya no es el de su dueño. Acá se cambia de hogar sin pasar por el canje, que es
    // justamente lo que dejaría el token sin revocar.
    @Test
    void elTokenDeUnHogarQueSuDuenoDejoSeRechaza() throws Exception {
        String token = emitir(ana, casaA);
        jdbcTemplate.update("update users set household_id = ? where id = ?", casaB, ana);

        rechazado(sonda(token));
        assertThat(ultimoUso(token)).isNull();
    }

    @Test
    void elTokenDeUnHogarArchivadoSeRechaza() throws Exception {
        String token = emitir(ana, casaA);
        jdbcTemplate.update("update households set active = false, archived_at = now() where id = ?", casaA);

        rechazado(sonda(token));
        assertThat(ultimoUso(token)).isNull();
    }

    // Si el token se revoca entre que se lee y que se registra su uso, la escritura no lo alcanza
    // y el filtro rechaza la petición.
    @Test
    void elUltimoUsoNoSeRegistraEnUnTokenRevocado() {
        String token = emitir(ana, casaA);
        UUID id = jdbcTemplate.queryForObject(
                "select id from machine_tokens where token_hash = ?", UUID.class, values.hash(token));
        jdbcTemplate.update("update machine_tokens set revoked_at = now() where id = ?", id);

        assertThat(machineTokens.markUsed(id, Instant.now())).isZero();
        assertThat(ultimoUso(token)).isNull();
    }

    @Test
    void sinTokenLaCapturaPideElTokenDeLaAutomatizacion() throws Exception {
        rechazado(mockMvc.perform(get(CaptureProbe.PATH)));
    }

    // El error más común al configurar una automatización es mandar el token como Bearer. Ahí lo
    // valida el filtro de la sesión, y el 401 tiene que seguir siendo el del canal: el de la
    // sesión pediría iniciar sesión, que una automatización no puede hacer.
    @Test
    void conBearerLaCapturaRespondeElRechazoDelCanal() throws Exception {
        String token = emitir(ana, casaA);
        String sesionVencida = token(ana, Instant.now().minus(Duration.ofDays(30)));

        for (String header : List.of("Bearer " + token, "Bearer no-es-un-jwt", "Bearer " + sesionVencida)) {
            rechazado(mockMvc.perform(get(CaptureProbe.PATH).header(AUTHORIZATION, header)));
        }
        assertThat(ultimoUso(token)).isNull();
    }

    // HU-17: en cualquier otra ruta el token se ignora, y la petición queda sin autenticar.
    @Test
    void elTokenNoHabilitaElRestoDeLaApi() throws Exception {
        String token = emitir(ana, casaA);

        for (String ruta : List.of("/api/auth/me", "/api/household", "/api/household/members")) {
            mockMvc.perform(get(ruta).header(AUTHORIZATION, "Token " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value(ProblemDetailAuthenticationEntryPoint.DETAIL));
        }
        assertThat(ultimoUso(token)).isNull();
    }

    // MockMvc corre la petición en el hilo de la prueba, así que si el hogar no se limpiara al
    // terminar, quedaría acá.
    @Test
    void elHogarNoQuedaEnElHiloAlTerminarLaPeticion() throws Exception {
        sonda(emitir(ana, casaA)).andExpect(status().isOk());

        assertThat(CurrentHousehold.get()).isEmpty();
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────

    /** Inserta un token vigente y devuelve su valor, como lo entregaría la emisión. */
    private String emitir(UUID usuario, UUID hogar) {
        String token = values.generate();
        jdbcTemplate.update(
                "insert into machine_tokens (household_id, user_id, name, token_hash) values (?, ?, ?, ?)",
                hogar, usuario, "Atajo del teléfono", values.hash(token));
        return token;
    }

    private ResultActions sonda(String token) throws Exception {
        return mockMvc.perform(get(CaptureProbe.PATH).header(AUTHORIZATION, "Token " + token));
    }

    /** El mismo 401 sin importar el motivo, con el esquema que espera el canal. */
    private static void rechazado(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, "Token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(MachineTokenAuthenticationEntryPoint.DETAIL))
                .andExpect(jsonPath("$.instance").value(CaptureProbe.PATH));
    }

    private Instant ultimoUso(String token) {
        return jdbcTemplate.queryForObject(
                "select last_used_at from machine_tokens where token_hash = ?", Instant.class, values.hash(token));
    }

    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        CaptureProbe captureProbe(CategoryRepository categories) {
            return new CaptureProbe(categories);
        }
    }

    @RestController
    static class CaptureProbe {

        static final String PATH = "/api/capture/probe";

        private final CategoryRepository categories;

        CaptureProbe(CategoryRepository categories) {
            this.categories = categories;
        }

        @GetMapping(PATH)
        public Map<String, Object> probe(@AuthenticationPrincipal AuthenticatedUser user) {
            return Map.of(
                    "userId", user.userId(),
                    "householdId", user.householdId(),
                    "categories", categories.findAllByActiveTrueOrderByNameAsc().stream()
                            .map(Category::getName)
                            .toList());
        }
    }
}