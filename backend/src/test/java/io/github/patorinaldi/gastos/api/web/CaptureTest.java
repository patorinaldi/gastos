package io.github.patorinaldi.gastos.api.web;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import io.github.patorinaldi.gastos.api.service.auth.MachineTokenValues;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Captura rápida: {@code POST /api/capture} (RF-30 a RF-32, HU-16, CU-07).
 *
 * <p>Los rechazos del token en sí (inexistente, revocado, de un hogar que su dueño dejó o archivado)
 * los cubre {@code MachineTokenAuthenticationTest} contra una ruta de prueba. Acá se repite el del
 * token revocado, que es el escenario de la HU-16, contra el punto de entrada real.
 */
class CaptureTest extends AuthTestSupport {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Autowired
    private MachineTokenValues values;

    private UUID casa;
    private UUID ana;
    private UUID supermercado;
    private String token;

    private UUID otraCasa;

    @BeforeEach
    void sembrar() {
        casa = createHousehold("Casa de Ana");
        ana = createVerifiedUser(casa, uniqueEmail("ana"), "Ana", "contraseña-de-prueba");
        supermercado = UUID.randomUUID();
        withHousehold(casa, () -> {
            jdbcTemplate.update("insert into categories (id, household_id, name) values (?, ?, ?)",
                    supermercado, casa, "Supermercado");
            jdbcTemplate.update("insert into category_rules (household_id, category_id, pattern) values (?, ?, ?)",
                    casa, supermercado, "carrefour");
        });
        token = issueToken(casa, ana);

        otraCasa = createHousehold("Casa de Carla");
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
        deleteHousehold(otraCasa);
    }

    // ── HU-16 ───────────────────────────────────────────────────────────────

    // Formato local, categoría por las reglas del hogar, fecha de hoy y tarjeta por defecto.
    @Test
    void registraElGastoConElImporteNormalizadoYLaCategoriaDeLasReglas() throws Exception {
        String body = capture(token, "1.234,56", "Carrefour")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("1234.56"))
                .andExpect(jsonPath("$.merchant").value("Carrefour"))
                .andExpect(jsonPath("$.categoryId").value(supermercado.toString()))
                .andExpect(jsonPath("$.expenseDate").value(LocalDate.now(ZONE).toString()))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> saved = savedExpense(UUID.fromString(JsonPath.read(body, "$.id")));
        assertThat(saved)
                .containsEntry("household_id", casa)
                .containsEntry("owner_id", ana)
                .containsEntry("payment_method", "Tarjeta");
    }

    @Test
    void interpretaElFormatoAnglosajon() throws Exception {
        capture(token, "1,234.56", "Carrefour").andExpect(jsonPath("$.amount").value("1234.56"));
    }

    // RF-21: sin coincidencia, el gasto queda en la bandeja.
    @Test
    void sinReglaQueCoincidaElGastoQuedaSinCategoria() throws Exception {
        capture(token, "500", "Kiosco de la esquina")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").doesNotExist());
    }

    @Test
    void elMedioDePagoSePuedeIndicar() throws Exception {
        String body = mockMvc.perform(post("/api/capture")
                        .header(AUTHORIZATION, "Token " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": "800", "merchant": "Verdulería", "paymentMethod": "Transferencia"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(savedExpense(UUID.fromString(JsonPath.read(body, "$.id"))))
                .containsEntry("payment_method", "Transferencia");
    }

    // El comercio se leyó de una notificación: queda guardado sin saltos de línea ni espacios de más.
    @Test
    void elComercioSeGuardaNormalizado() throws Exception {
        capture(token, "1.000", "  CARREFOUR\\n  Express ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchant").value("CARREFOUR Express"))
                .andExpect(jsonPath("$.categoryId").value(supermercado.toString()));
    }

    // CU-07, 3a: un importe ambiguo no se registra con un valor adivinado.
    @Test
    void unImporteQueNoSePuedeInterpretarSeRechazaYNoSeRegistra() throws Exception {
        for (String amount : new String[] {"1.234,567", "doce", "-500", "1.23.4"}) {
            capture(token, amount, "Carrefour").andExpect(status().isBadRequest());
        }

        assertThat(expensesOf(casa)).isZero();
    }

    // HU-16, token revocado.
    @Test
    void unTokenRevocadoSeRechaza() throws Exception {
        jdbcTemplate.update("update machine_tokens set revoked_at = now() where token_hash = ?", (Object) values.hash(token));

        capture(token, "100", "Carrefour")
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Token")));

        assertThat(expensesOf(casa)).isZero();
    }

    // ── Autenticación ───────────────────────────────────────────────────────

    @Test
    void sinTokenSeRechaza() throws Exception {
        mockMvc.perform(post("/api/capture").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": \"100\", \"merchant\": \"Carrefour\"}"))
                .andExpect(status().isUnauthorized());
    }

    // El canal es propio: una sesión web no sirve para capturar.
    @Test
    void unaSesionWebNoSirveParaCapturar() throws Exception {
        mockMvc.perform(post("/api/capture")
                        .header(AUTHORIZATION, bearer(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": \"100\", \"merchant\": \"Carrefour\"}"))
                .andExpect(status().isForbidden());

        assertThat(expensesOf(casa)).isZero();
    }

    // El gasto queda en el hogar del token: otro hogar no lo ve.
    @Test
    void elGastoQuedaEnElHogarDelTokenYOtroHogarNoLoVe() throws Exception {
        capture(token, "100", "Carrefour").andExpect(status().isCreated());

        assertThat(expensesOf(casa)).isEqualTo(1);
        assertThat(expensesOf(otraCasa)).isZero();
    }

    // Las reglas son las del hogar del token: una de otro hogar que coincide con el comercio no se aplica.
    @Test
    void lasReglasDeOtroHogarNoSeAplican() throws Exception {
        UUID deOtroHogar = UUID.randomUUID();
        withHousehold(otraCasa, () -> {
            jdbcTemplate.update("insert into categories (id, household_id, name) values (?, ?, ?)",
                    deOtroHogar, otraCasa, "Kioscos");
            jdbcTemplate.update("insert into category_rules (household_id, category_id, pattern) values (?, ?, ?)",
                    otraCasa, deOtroHogar, "kiosco");
        });

        capture(token, "500", "Kiosco de la esquina")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").doesNotExist());
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────

    private String issueToken(UUID household, UUID user) {
        String value = values.generate();
        jdbcTemplate.update("insert into machine_tokens (household_id, user_id, name, token_hash) values (?, ?, ?, ?)",
                household, user, "Atajo del teléfono", values.hash(value));
        return value;
    }

    private ResultActions capture(String machineToken, String amount, String merchant) throws Exception {
        return mockMvc.perform(post("/api/capture")
                .header(AUTHORIZATION, "Token " + machineToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": "%s", "merchant": "%s"}""".formatted(amount, merchant)));
    }

    private Map<String, Object> savedExpense(UUID id) {
        return withHousehold(casa, () -> jdbcTemplate.queryForMap(
                "select household_id, owner_id, payment_method from expenses where id = ?", id));
    }

    private int expensesOf(UUID household) {
        return withHousehold(household, () -> jdbcTemplate.queryForObject(
                "select count(*) from expenses", Integer.class));
    }
}
