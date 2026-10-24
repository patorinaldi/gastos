package io.github.patorinaldi.gastos.api.web;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import io.github.patorinaldi.gastos.api.config.MachineTokenLimitProperties;
import io.github.patorinaldi.gastos.api.repository.MachineTokenRepository;
import io.github.patorinaldi.gastos.api.security.MachineTokenOwner;
import io.github.patorinaldi.gastos.api.service.auth.MachineTokenValues;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Emisión, listado y revocación de tokens de cliente máquina: {@code /api/auth/machine-tokens}
 * (RF-07, HU-17, RNF-33). Que un token emitido autentique la captura lo cubre
 * {@code MachineTokenAuthenticationTest}; acá se comprueba con la misma consulta que usa ese filtro.
 */
class MachineTokenManagementTest extends AuthTestSupport {

    @Autowired
    private MachineTokenRepository repository;

    @Autowired
    private MachineTokenValues values;

    @Autowired
    private MachineTokenLimitProperties limits;

    private UUID casa;
    private UUID ana;
    private UUID beto;
    private UUID otraCasa;
    private UUID carla;

    @BeforeEach
    void sembrar() {
        casa = createHousehold("Casa de Ana");
        ana = createUser(casa, uniqueEmail("ana"), "Ana");
        beto = createUser(casa, uniqueEmail("beto"), "Beto");
        otraCasa = createHousehold("Casa de Carla");
        carla = createUser(otraCasa, uniqueEmail("carla"), "Carla");
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
        deleteHousehold(otraCasa);
    }

    // ── Emisión ─────────────────────────────────────────────────────────────

    // HU-17: un token de larga duración, asociado al hogar y a la persona.
    @Test
    void emitirDevuelveElTokenUnaSolaVezAsociadoAlHogarYALaPersona() throws Exception {
        String body = issue(ana, "  Atajo del teléfono  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Atajo del teléfono"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));

        assertThat(values.isWellFormed(token)).isTrue();
        MachineTokenOwner owner = repository.findOwner(values.hash(token)).orElseThrow();
        assertThat(owner.tokenId()).isEqualTo(id);
        assertThat(owner.userId()).isEqualTo(ana);
        assertThat(owner.tokenHouseholdId()).isEqualTo(casa);
        assertThat(owner.revokedAt()).isNull();
    }

    // La base guarda solo el hash: el valor en claro existe solo en la respuesta de la emisión.
    @Test
    void elListadoNoMuestraElValorNiElHash() throws Exception {
        issue(ana, "Atajo").andExpect(status().isCreated());

        list(ana)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Atajo"))
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andExpect(jsonPath("$[0].tokenHash").doesNotExist());
    }

    @Test
    void unNombreEnBlancoSeRechaza() throws Exception {
        issue(ana, "   ").andExpect(status().isBadRequest());
    }

    // RNF-33. Revocar uno libera el lugar.
    @Test
    void superadoElTopeDeVigentesNoEmiteHastaRevocarUno() throws Exception {
        String primero = null;
        for (int i = 0; i < limits.maxActivePerUser(); i++) {
            String body = issue(ana, "Token " + i).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            if (primero == null) {
                primero = JsonPath.read(body, "$.id");
            }
        }

        issue(ana, "Uno más").andExpect(status().isConflict());

        revoke(ana, UUID.fromString(primero)).andExpect(status().isNoContent());
        issue(ana, "Uno más").andExpect(status().isCreated());
    }

    // ── Listado ─────────────────────────────────────────────────────────────

    // Un token es una credencial de quien lo emitió: otra persona del mismo hogar no lo ve.
    @Test
    void cadaPersonaVeSoloSusTokensDelMasNuevoAlMasViejo() throws Exception {
        issue(ana, "Primero").andExpect(status().isCreated());
        issue(ana, "Segundo").andExpect(status().isCreated());
        issue(beto, "De Beto").andExpect(status().isCreated());

        list(ana)
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Segundo"))
                .andExpect(jsonPath("$[1].name").value("Primero"));
        list(beto).andExpect(jsonPath("$", hasSize(1)));
        list(carla).andExpect(jsonPath("$", hasSize(0)));
    }

    // ── Revocación ──────────────────────────────────────────────────────────

    // HU-17: las capturas con ese token se rechazan, y la sesión web sigue activa.
    @Test
    void revocarDejaElTokenSinEfectoYLaSesionSigueActiva() throws Exception {
        String sesion = bearer(ana);
        String body = issue(ana, "Teléfono perdido").andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));

        mockMvc.perform(delete("/api/auth/machine-tokens/{id}", id).header(AUTHORIZATION, sesion))
                .andExpect(status().isNoContent());

        assertThat(repository.findOwner(values.hash(token)).orElseThrow().revokedAt()).isNotNull();
        list(ana).andExpect(jsonPath("$[0].revokedAt").isString());
        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, sesion)).andExpect(status().isOk());
    }

    @Test
    void revocarUnTokenYaRevocadoNoEsUnErrorYConservaElMomentoDeRevocacion() throws Exception {
        UUID id = issuedId(ana);
        revoke(ana, id).andExpect(status().isNoContent());
        Object revocadoEn = jdbcTemplate.queryForObject("select revoked_at from machine_tokens where id = ?", Object.class, id);

        revoke(ana, id).andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("select revoked_at from machine_tokens where id = ?", Object.class, id))
                .isEqualTo(revocadoEn);
    }

    // Ni alguien del mismo hogar ni alguien de otro hogar pueden revocarlo, aunque conozcan el id,
    // y la respuesta es la misma que para un id inexistente.
    @Test
    void nadieMasPuedeRevocarUnTokenAjeno() throws Exception {
        UUID id = issuedId(ana);

        revoke(beto, id).andExpect(status().isNotFound());
        revoke(carla, id).andExpect(status().isNotFound());
        revoke(ana, UUID.randomUUID()).andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject("select revoked_at from machine_tokens where id = ?", Object.class, id))
                .isNull();
    }

    // ── Autenticación ───────────────────────────────────────────────────────

    @Test
    void sinSesionNadaResponde() throws Exception {
        mockMvc.perform(get("/api/auth/machine-tokens")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/machine-tokens").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Atajo\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/auth/machine-tokens/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    // HU-17: el token solo sirve para la captura. Con él no se administran tokens, ni presentado
    // como token de máquina ni como si fuera de sesión.
    @Test
    void unTokenDeClienteMaquinaNoSirveParaAdministrarTokens() throws Exception {
        String token = JsonPath.read(issue(ana, "Atajo").andReturn().getResponse().getContentAsString(), "$.token");

        for (String header : List.of("Token " + token, "Bearer " + token)) {
            mockMvc.perform(get("/api/auth/machine-tokens").header(AUTHORIZATION, header))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────

    private ResultActions issue(UUID user, String name) throws Exception {
        return mockMvc.perform(post("/api/auth/machine-tokens")
                .header(AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s"}""".formatted(name)));
    }

    private UUID issuedId(UUID user) throws Exception {
        String body = issue(user, "Atajo").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private ResultActions list(UUID user) throws Exception {
        return mockMvc.perform(get("/api/auth/machine-tokens").header(AUTHORIZATION, bearer(user)));
    }

    private ResultActions revoke(UUID user, UUID tokenId) throws Exception {
        return mockMvc.perform(delete("/api/auth/machine-tokens/{id}", tokenId).header(AUTHORIZATION, bearer(user)));
    }
}
