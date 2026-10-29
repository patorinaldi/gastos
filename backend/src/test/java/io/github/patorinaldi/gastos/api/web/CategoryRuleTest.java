package io.github.patorinaldi.gastos.api.web;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Motor de categorización y reglas: la resolución en el alta de un gasto, la bandeja y
 * {@code /api/category-rules} (RF-20 a RF-23, RF-43, HU-08, HU-11, RN-10, RN-12, RN-13, RN-16, RN-17).
 *
 * <p>Cada prueba siembra su hogar y otro con gastos sin categoría en los mismos comercios: una regla
 * de este hogar nunca tiene que clasificarlos.
 */
class CategoryRuleTest extends AuthTestSupport {

    private static final LocalDate SEPTIEMBRE = LocalDate.of(2026, 9, 10);
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private UUID casa;
    private UUID ana;
    private UUID supermercado;
    private UUID transporte;
    private UUID restaurantes;
    private UUID hogar;

    private UUID otraCasa;
    private UUID carla;
    private UUID deOtroHogar;
    private UUID gastoDeOtroHogar;

    @BeforeEach
    void sembrar() {
        casa = createHousehold("Casa de Ana");
        ana = createUser(casa, uniqueEmail("ana"), "Ana");
        supermercado = category(casa, "Supermercado");
        transporte = category(casa, "Transporte");
        restaurantes = category(casa, "Restaurantes y delivery");
        hogar = category(casa, "Hogar");

        otraCasa = createHousehold("Casa de Carla");
        carla = createUser(otraCasa, uniqueEmail("carla"), "Carla");
        deOtroHogar = category(otraCasa, "Supermercado");
        gastoDeOtroHogar = expense(otraCasa, carla, null, "Almacén de Pepe", SEPTIEMBRE);
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
        deleteHousehold(otraCasa);
    }

    // ── Resolución en el alta (HU-08) ───────────────────────────────────────

    @Test
    void elAltaResuelveLaCategoriaPorLasReglas() throws Exception {
        rule(casa, "carrefour", supermercado, T0);

        createExpense("{\"amount\": \"1234.56\", \"merchant\": \"CARREFOUR Express\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoryId").value(supermercado.toString()))
                .andExpect(jsonPath("$.categoryName").value("Supermercado"));
    }

    // RN-17: con "uber" y "uber eats", un pedido de comida no queda como transporte.
    @Test
    void coincidenVariasReglasYGanaElPatronMasLargo() throws Exception {
        rule(casa, "uber", transporte, T0);
        rule(casa, "uber eats", restaurantes, T0.plusSeconds(60));

        createExpense("{\"amount\": \"10.00\", \"merchant\": \"Uber Eats\"}")
                .andExpect(jsonPath("$.categoryName").value("Restaurantes y delivery"));
        createExpense("{\"amount\": \"10.00\", \"merchant\": \"Uber\"}")
                .andExpect(jsonPath("$.categoryName").value("Transporte"));
    }

    // RF-21, RN-12: sin coincidencia, a la bandeja; nunca a una categoría de descarte.
    @Test
    void sinReglaQueCoincidaElGastoVaALaBandeja() throws Exception {
        rule(casa, "carrefour", supermercado, T0);

        createExpense("{\"amount\": \"10.00\", \"merchant\": \"Almacén de Pepe\"}")
                .andExpect(jsonPath("$.categoryId", nullValue()));
    }

    // Una categoría elegida a mano manda sobre las reglas.
    @Test
    void laCategoriaElegidaAManoMandaSobreLasReglas() throws Exception {
        rule(casa, "carrefour", supermercado, T0);

        createExpense("{\"amount\": \"10.00\", \"merchant\": \"Carrefour\", \"categoryId\": \"%s\"}".formatted(hogar))
                .andExpect(jsonPath("$.categoryName").value("Hogar"));
    }

    // Las reglas de otro hogar no se aplican.
    @Test
    void lasReglasDeOtroHogarNoSeAplican() throws Exception {
        rule(otraCasa, "carrefour", deOtroHogar, T0);

        createExpense("{\"amount\": \"10.00\", \"merchant\": \"Carrefour\"}")
                .andExpect(jsonPath("$.categoryId", nullValue()));
    }

    // ── Bandeja (RF-22) ─────────────────────────────────────────────────────

    // La bandeja es una lista de trabajo: sin período, muestra los pendientes de cualquier mes.
    @Test
    void laBandejaSinPeriodoMuestraLosPendientesDeCualquierMes() throws Exception {
        expense(casa, ana, null, "Kiosco de marzo", LocalDate.of(2026, 3, 10));
        expense(casa, ana, null, "Kiosco de septiembre", SEPTIEMBRE);
        expense(casa, ana, supermercado, "Carrefour", SEPTIEMBRE);

        mockMvc.perform(authorized(get("/api/expenses").param("categoryId", "uncategorized")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].merchant", contains("Kiosco de septiembre", "Kiosco de marzo")));
    }

    // ── Alta de regla y reclasificación (HU-11, RN-13) ──────────────────────

    // El escenario de la HU-11: los gastos del comercio salen de la bandeja, y el patrón se guarda
    // normalizado aunque llegue escrito como el comercio.
    @Test
    void crearUnaReglaVaciaLaBandejaDeEseComercio() throws Exception {
        for (int i = 0; i < 7; i++) {
            expense(casa, ana, null, "Almacén de Pepe", SEPTIEMBRE.minusMonths(i));
        }
        expense(casa, ana, null, "Kiosco", SEPTIEMBRE);

        createRule("Almacén de  Pepe", supermercado)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pattern").value("almacen de pepe"))
                .andExpect(jsonPath("$.categoryId").value(supermercado.toString()))
                .andExpect(jsonPath("$.reclassified").value(7));

        mockMvc.perform(authorized(get("/api/expenses").param("categoryId", "uncategorized")))
                .andExpect(jsonPath("$.content[*].merchant", contains("Kiosco")));
        assertThat(categoryOf(otraCasa, gastoDeOtroHogar)).isNull();
    }

    // RN-13: lo ya clasificado no se pisa, y lo dado de baja no se toca.
    @Test
    void laReglaNoPisaClasificacionesPreviasNiTocaGastosDadosDeBaja() throws Exception {
        UUID clasificado = expense(casa, ana, hogar, "Almacén de Pepe", SEPTIEMBRE);
        UUID dadoDeBaja = expense(casa, ana, null, "Almacén de Pepe", SEPTIEMBRE);
        withHousehold(casa, () -> jdbcTemplate.update(
                "update expenses set active = false, deleted_at = now() where id = ?", dadoDeBaja));

        createRule("almacen de pepe", supermercado).andExpect(jsonPath("$.reclassified").value(0));

        assertThat(categoryOf(casa, clasificado)).isEqualTo(hogar);
        assertThat(categoryOf(casa, dadoDeBaja)).isNull();
    }

    // RN-17 también en la reclasificación: con "uber eats" ya existente, crear "uber" deja cada gasto
    // de la bandeja donde lo habría dejado el alta.
    @Test
    void laReclasificacionUsaElMotorCompleto() throws Exception {
        rule(casa, "uber eats", restaurantes, T0);
        UUID comida = expense(casa, ana, null, "Uber Eats", SEPTIEMBRE);
        UUID viaje = expense(casa, ana, null, "Uber Trip", SEPTIEMBRE);

        createRule("uber", transporte).andExpect(jsonPath("$.reclassified").value(2));

        assertThat(categoryOf(casa, comida)).isEqualTo(restaurantes);
        assertThat(categoryOf(casa, viaje)).isEqualTo(transporte);
    }

    // RN-10, CU-05 4a: se informa a qué categoría apunta la regla existente. "burger king" y "burgerking"
    // coinciden con los mismos comercios: son el mismo patrón.
    @Test
    void rechazaUnPatronRepetido() throws Exception {
        rule(casa, "burger king", restaurantes, T0);

        for (String patron : new String[] {"burger king", "BURGER  KING", "Burger-King", "burgerking"}) {
            createRule(patron, supermercado)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail")
                            .value("Ya existe una regla con ese patrón, para la categoría «Restaurantes y delivery»."));
        }
    }

    @Test
    void rechazaPatronesDemasiadoCortosOVacios() throws Exception {
        for (String patron : new String[] {"ab", "a.b", "--", "   ", ""}) {
            createRule(patron, supermercado).andExpect(status().isBadRequest());
        }
        createRule("ypf", transporte).andExpect(status().isCreated());
    }

    // NFKD alarga algunos caracteres: 200 ligaduras "ﬃ" pasan el Size del contrato y quedan en 600
    // caracteres. Antes llegaban al insert y la base respondía con un 500.
    @Test
    void rechazaUnPatronQueAlNormalizarloNoEntraEnLaColumna() throws Exception {
        createRule("ﬃ".repeat(200), supermercado)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("El patrón no puede tener más de 200 caracteres."));
        // "㎏" pasa a ser "kg": 101 son 202 caracteres.
        createRule("㎏".repeat(101), supermercado).andExpect(status().isBadRequest());
        mockMvc.perform(authorized(get("/api/category-rules"))).andExpect(jsonPath("$").isEmpty());
    }

    // La reclasificación usa la misma coincidencia que el alta: "uber" no clasifica "Pub Ernesto".
    @Test
    void laReclasificacionNoTomaUnPatronEnMedioDeUnaPalabra() throws Exception {
        UUID pub = expense(casa, ana, null, "Pub Ernesto", SEPTIEMBRE);
        UUID viaje = expense(casa, ana, null, "MERPAGO*UBER", SEPTIEMBRE);

        createRule("uber", transporte).andExpect(jsonPath("$.reclassified").value(1));

        assertThat(categoryOf(casa, pub)).isNull();
        assertThat(categoryOf(casa, viaje)).isEqualTo(transporte);
    }

    // RN-08, RN-16: la categoría tiene que ser del hogar y estar activa.
    @Test
    void rechazaUnaCategoriaDadaDeBajaODeOtroHogar() throws Exception {
        withHousehold(casa, () -> jdbcTemplate.update(
                "update categories set active = false, deleted_at = now() where id = ?", hogar));

        for (UUID categoria : new UUID[] {hogar, deOtroHogar, UUID.randomUUID()}) {
            createRule("almacen de pepe", categoria)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("La categoría no existe o está dada de baja."));
        }
    }

    // ── Listado y eliminación (RF-43) ───────────────────────────────────────

    @Test
    void listaLasReglasDelHogarConSuCategoria() throws Exception {
        rule(casa, "uber", transporte, T0);
        rule(casa, "carrefour", supermercado, T0);
        rule(otraCasa, "coto", deOtroHogar, T0);

        mockMvc.perform(authorized(get("/api/category-rules")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].pattern", contains("carrefour", "uber")))
                .andExpect(jsonPath("$[*].categoryName", contains("Supermercado", "Transporte")));
    }

    // RF-43: eliminar una regla no cambia los gastos que ya clasificó, y el patrón queda libre.
    @Test
    void eliminarUnaReglaNoAlteraLosGastosClasificados() throws Exception {
        UUID gasto = expense(casa, ana, null, "Carrefour", SEPTIEMBRE);
        String body = createRule("carrefour", supermercado).andReturn().getResponse().getContentAsString();
        String regla = JsonPath.read(body, "$.id");

        mockMvc.perform(authorized(delete("/api/category-rules/{id}", regla))).andExpect(status().isNoContent());

        assertThat(categoryOf(casa, gasto)).isEqualTo(supermercado);
        mockMvc.perform(authorized(get("/api/category-rules"))).andExpect(jsonPath("$").isEmpty());
        createRule("carrefour", hogar).andExpect(status().isCreated());
        mockMvc.perform(authorized(delete("/api/category-rules/{id}", regla))).andExpect(status().isNotFound());
    }

    @Test
    void noSeEliminaUnaReglaDeOtroHogar() throws Exception {
        UUID ajena = rule(otraCasa, "coto", deOtroHogar, T0);

        mockMvc.perform(authorized(delete("/api/category-rules/{id}", ajena)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe esa regla."));
        assertThat(withHousehold(otraCasa, () -> jdbcTemplate.queryForObject(
                "select count(*) from category_rules where id = ?", Integer.class, ajena))).isEqualTo(1);
    }

    @Test
    void sinSesionNadaResponde() throws Exception {
        UUID regla = rule(casa, "uber", transporte, T0);

        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get("/api/category-rules"),
                post("/api/category-rules").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pattern\": \"coto\", \"categoryId\": \"%s\"}".formatted(supermercado)),
                delete("/api/category-rules/{id}", regla)}) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────

    private ResultActions createExpense(String body) throws Exception {
        return mockMvc.perform(authorized(post("/api/expenses")).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions createRule(String pattern, UUID category) throws Exception {
        return mockMvc.perform(authorized(post("/api/category-rules")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"pattern\": \"%s\", \"categoryId\": \"%s\"}".formatted(pattern, category)));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request) {
        return request.header(AUTHORIZATION, bearer(ana));
    }

    private UUID category(UUID household, String name) {
        UUID id = UUID.randomUUID();
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into categories (id, household_id, name) values (?, ?, ?)", id, household, name));
        return id;
    }

    private UUID rule(UUID household, String pattern, UUID category, Instant createdAt) {
        UUID id = UUID.randomUUID();
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into category_rules (id, household_id, pattern, category_id, created_at) values (?, ?, ?, ?, ?)",
                id, household, pattern, category, Timestamp.from(createdAt)));
        return id;
    }

    private UUID expense(UUID household, UUID owner, UUID category, String merchant, LocalDate date) {
        UUID id = UUID.randomUUID();
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into expenses (id, household_id, owner_id, category_id, merchant, amount, expense_date,"
                        + " payment_method) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, household, owner, category, merchant, new BigDecimal("100.00"), date, "Tarjeta"));
        return id;
    }

    private UUID categoryOf(UUID household, UUID expense) {
        return withHousehold(household, () -> jdbcTemplate.queryForObject(
                "select category_id from expenses where id = ?", UUID.class, expense));
    }
}
