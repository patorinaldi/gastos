package io.github.patorinaldi.gastos.api.web;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sumarse a otro hogar con un código: vista previa y canje (HU-06, CU-03; RF-09, RF-12, RN-05,
 * RN-18).
 *
 * <p>Ana integra "Casa de Ana" y Juan, "Casa de Juan". Juan genera el código y Ana lo canjea.
 */
class InvitationRedeemTest extends AuthTestSupport {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UUID casaDeAna;
    private UUID casaDeJuan;
    private UUID ana;
    private UUID juan;

    @BeforeEach
    void sembrarDosHogares() {
        casaDeAna = createHousehold("Casa de Ana");
        ana = createUser(casaDeAna, uniqueEmail("ana"), "Ana");
        casaDeJuan = createHousehold("Casa de Juan");
        juan = createUser(casaDeJuan, uniqueEmail("juan"), "Juan");
    }

    // El hogar que Ana deja primero: sus gastos ahí la nombran como responsable.
    @AfterEach
    void limpiar() {
        deleteHousehold(casaDeAna);
        deleteHousehold(casaDeJuan);
    }

    // ── Vista previa ────────────────────────────────────────────────────────────────────────

    @Test
    void laVistaPreviaDescribeLasConsecuenciasSinAplicarlas() throws Exception {
        insertarToken(ana, casaDeAna);
        insertarToken(ana, casaDeAna);
        UUID revocado = insertarToken(ana, casaDeAna);
        jdbcTemplate.update("update machine_tokens set revoked_at = now() where id = ?", revocado);
        String codigo = codigoDe(juan);

        vistaPrevia(ana, codigo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdName").value("Casa de Juan"))
                .andExpect(jsonPath("$.currentHouseholdWillBeArchived").value(true))
                .andExpect(jsonPath("$.machineTokensToRevoke").value(2));

        assertThat(hogarDe(ana)).isEqualTo(casaDeAna);
        assertThat(invitacionCanjeada(codigo)).isFalse();
        assertThat(tokensVigentes(ana, casaDeAna)).isEqualTo(2);
        assertThat(activo(casaDeAna)).isTrue();
    }

    @Test
    void conOtroIntegranteLaVistaPreviaAvisaQueElHogarNoSeArchiva() throws Exception {
        createUser(casaDeAna, uniqueEmail("bruno"), "Bruno");

        vistaPrevia(ana, codigoDe(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentHouseholdWillBeArchived").value(false))
                .andExpect(jsonPath("$.machineTokensToRevoke").value(0));
    }

    // Quien lo recibe por chat lo tipea como puede.
    @Test
    void elCodigoSeReconoceAunqueSeTipeeDistinto() throws Exception {
        String codigo = codigoDe(juan);

        vistaPrevia(ana, "  " + codigo.toLowerCase().replace("-", " ") + " ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdName").value("Casa de Juan"));
    }

    // ── Canje ───────────────────────────────────────────────────────────────────────────────

    @Test
    void siendoLaUnicaIntegranteElCanjeLaCambiaDeHogarYArchivaElAnterior() throws Exception {
        UUID gasto = insertarGasto(casaDeAna, ana);
        insertarToken(ana, casaDeAna);
        insertarToken(ana, casaDeAna);
        String codigo = codigoDe(juan);

        canjear(ana, codigo, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId").value(casaDeJuan.toString()))
                .andExpect(jsonPath("$.householdName").value("Casa de Juan"))
                .andExpect(jsonPath("$.previousHouseholdArchived").value(true));

        assertThat(hogarDe(ana)).isEqualTo(casaDeJuan);
        assertThat(jdbcTemplate.queryForObject(
                "select redeemed_by from household_invitations where household_id = ?", UUID.class, casaDeJuan))
                .isEqualTo(ana);
        assertThat(jdbcTemplate.queryForObject(
                "select redeemed_at is not null from household_invitations where household_id = ?",
                Boolean.class, casaDeJuan)).isTrue();
        assertThat(tokensVigentes(ana, casaDeAna)).isZero();
        assertThat(activo(casaDeAna)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select archived_at is not null from households where id = ?", Boolean.class, casaDeAna)).isTrue();
        // El gasto se quedó en el hogar anterior, a su nombre (RN-18).
        assertThat(responsableDelGasto(casaDeAna, gasto)).isEqualTo(ana);
    }

    @Test
    void conOtroIntegranteElHogarAnteriorSigueActivoConLosGastosDeQuienSeFue() throws Exception {
        createUser(casaDeAna, uniqueEmail("bruno"), "Bruno");
        UUID gasto = insertarGasto(casaDeAna, ana);

        canjear(ana, codigoDe(juan), true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousHouseholdArchived").value(false));

        assertThat(hogarDe(ana)).isEqualTo(casaDeJuan);
        assertThat(activo(casaDeAna)).isTrue();
        assertThat(responsableDelGasto(casaDeAna, gasto)).isEqualTo(ana);
    }

    // El hogar de cada petición se lee de la base, no del token: el emitido antes del canje ya
    // opera sobre el hogar nuevo.
    @Test
    void elTokenEmitidoAntesDelCanjeOperaSobreElHogarNuevo() throws Exception {
        String tokenPrevio = bearer(ana);

        canjear(ana, codigoDe(juan), true).andExpect(status().isOk());

        mockMvc.perform(get("/api/household").header(AUTHORIZATION, tokenPrevio))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(casaDeJuan.toString()));
    }

    @Test
    void sinConfirmacionNoSeCanjeaYNadaCambia() throws Exception {
        String codigo = codigoDe(juan);

        canjear(ana, codigo, false)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(hogarDe(ana)).isEqualTo(casaDeAna);
        assertThat(invitacionCanjeada(codigo)).isFalse();
        assertThat(activo(casaDeAna)).isTrue();
    }

    @Test
    void elCodigoSeCanjeaUnaSolaVez() throws Exception {
        String codigo = codigoDe(juan);
        UUID casaDeCarla = createHousehold("Casa de Carla");
        UUID carla = createUser(casaDeCarla, uniqueEmail("carla"), "Carla");
        try {
            canjear(ana, codigo, true).andExpect(status().isOk());

            canjear(carla, codigo, true).andExpect(status().isGone());

            assertThat(hogarDe(carla)).isEqualTo(casaDeCarla);
        } finally {
            deleteHousehold(casaDeCarla);
        }
    }

    // ── Rechazos ────────────────────────────────────────────────────────────────────────────

    // Inexistente, vencido, ya canjeado o de un hogar archivado responden exactamente igual, para
    // que no se puedan descubrir códigos válidos por tanteo (CU-03, 2b).
    @Test
    void todosLosCodigosInvalidosRespondenIgualYNoCambianNada() throws Exception {
        UUID casaDeCarla = createHousehold("Casa de Carla");
        UUID carla = createUser(casaDeCarla, uniqueEmail("carla"), "Carla");
        UUID casaArchivada = createHousehold("Casa archivada");
        UUID diego = createUser(casaArchivada, uniqueEmail("diego"), "Diego");
        try {
            String vencido = codigoDe(juan);
            jdbcTemplate.update("update household_invitations set expires_at = now() - interval '1 second'"
                    + " where household_id = ?", casaDeJuan);
            String canjeado = codigoDe(carla);
            canjear(juan, canjeado, true).andExpect(status().isOk());
            // Juan se fue a la casa de Carla: el hogar de Juan queda sin nadie y archivado.
            String deHogarArchivado = codigoDe(diego);
            jdbcTemplate.update("update households set active = false, archived_at = now() where id = ?",
                    casaArchivada);

            List<String> codigos = List.of("GST-2222-222", vencido, canjeado, deHogarArchivado);
            List<String> vistasPrevias = new ArrayList<>();
            List<String> canjes = new ArrayList<>();
            for (String codigo : codigos) {
                vistasPrevias.add(vistaPrevia(ana, codigo)
                        .andExpect(status().isGone())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .andReturn().getResponse().getContentAsString());
                canjes.add(canjear(ana, codigo, true)
                        .andExpect(status().isGone())
                        .andReturn().getResponse().getContentAsString());
            }

            assertThat(vistasPrevias).allMatch(vistasPrevias.getFirst()::equals);
            assertThat(canjes).allMatch(canjes.getFirst()::equals);
            assertThat(hogarDe(ana)).isEqualTo(casaDeAna);
            assertThat(activo(casaDeAna)).isTrue();
        } finally {
            // Juan dejó su hogar por el de Carla: se borra antes, como dice deleteHousehold.
            deleteHousehold(casaDeJuan);
            deleteHousehold(casaDeCarla);
            deleteHousehold(casaArchivada);
        }
    }

    // Sin este rechazo, la única integrante que canjeara su propio código archivaría el hogar en
    // el que sigue estando (CU-03, 3a).
    @Test
    void elCodigoDelHogarPropioSeRechazaSinConsumirse() throws Exception {
        String propio = codigoDe(ana);

        vistaPrevia(ana, propio)
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        canjear(ana, propio, true)
                .andExpect(status().isConflict());

        assertThat(invitacionCanjeada(propio)).isFalse();
        assertThat(activo(casaDeAna)).isTrue();
    }

    // El 410 se decide antes que el 409: un código vencido del propio hogar no confirma que existió.
    @Test
    void unCodigoVencidoDelHogarPropioRespondeComoCualquierCodigoInvalido() throws Exception {
        String propio = codigoDe(ana);
        jdbcTemplate.update("update household_invitations set expires_at = now() - interval '1 second'"
                + " where household_id = ?", casaDeAna);

        vistaPrevia(ana, propio).andExpect(status().isGone());
        canjear(ana, propio, true).andExpect(status().isGone());
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────

    private String codigoDe(UUID usuario) throws Exception {
        String body = mockMvc.perform(post("/api/household/invitations").header(AUTHORIZATION, bearer(usuario)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    private ResultActions vistaPrevia(UUID usuario, String codigo) throws Exception {
        return mockMvc.perform(post("/api/household/invitations/preview")
                .with(fromNewAddress())
                .header(AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s"}""".formatted(codigo)));
    }

    private ResultActions canjear(UUID usuario, String codigo, boolean confirmar) throws Exception {
        return mockMvc.perform(post("/api/household/invitations/redeem")
                .with(fromNewAddress())
                .header(AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s", "confirm": %s}""".formatted(codigo, confirmar)));
    }

    private UUID insertarToken(UUID usuario, UUID hogar) {
        UUID id = UUID.randomUUID();
        byte[] hash = new byte[32];
        RANDOM.nextBytes(hash);
        jdbcTemplate.update(
                "insert into machine_tokens (id, household_id, user_id, name, token_hash) values (?, ?, ?, ?, ?)",
                id, hogar, usuario, "Atajo del teléfono", hash);
        return id;
    }

    // expenses tiene RLS: se inserta y se lee con el hogar en contexto.
    private UUID insertarGasto(UUID hogar, UUID responsable) {
        UUID id = UUID.randomUUID();
        withHousehold(hogar, () -> jdbcTemplate.update(
                "insert into expenses (id, household_id, owner_id, merchant, amount, expense_date,"
                        + " payment_method) values (?, ?, ?, ?, ?, ?, ?)",
                id, hogar, responsable, "Farmacity", new BigDecimal("100.00"), LocalDate.of(2026, 10, 1),
                "Tarjeta"));
        return id;
    }

    private UUID responsableDelGasto(UUID hogar, UUID gasto) {
        return withHousehold(hogar, () -> jdbcTemplate.queryForObject(
                "select owner_id from expenses where id = ?", UUID.class, gasto));
    }

    private UUID hogarDe(UUID usuario) {
        return jdbcTemplate.queryForObject("select household_id from users where id = ?", UUID.class, usuario);
    }

    private boolean activo(UUID hogar) {
        return jdbcTemplate.queryForObject("select active from households where id = ?", Boolean.class, hogar);
    }

    private int tokensVigentes(UUID usuario, UUID hogar) {
        return jdbcTemplate.queryForObject(
                "select count(*) from machine_tokens where user_id = ? and household_id = ? and revoked_at is null",
                Integer.class, usuario, hogar);
    }

    private boolean invitacionCanjeada(String codigo) {
        return jdbcTemplate.queryForObject(
                "select redeemed_at is not null from household_invitations"
                        + " where code_hash = sha256(convert_to(?, 'UTF8'))",
                Boolean.class, codigo.replace("-", ""));
    }
}
