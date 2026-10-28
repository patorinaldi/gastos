package io.github.patorinaldi.gastos.api.web;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gastos: alta, detalle, edición, baja y listado de {@code /api/expenses} (RF-13 a RF-18, HU-08 a
 * HU-10, RN-06, RN-07, RN-08, RN-16, RN-18).
 *
 * <p>Cada prueba siembra su propio hogar y otro con gastos en las mismas fechas, que nunca tiene que
 * aparecer ni poder tocarse. La batería completa de aislamiento por endpoint es aparte; acá se cubre
 * lo que hace al contrato de cada uno: un gasto de otro hogar responde como uno inexistente.
 */
class ExpenseTest extends AuthTestSupport {

    private static final LocalDate SEPTIEMBRE = LocalDate.of(2026, 9, 10);

    // La fecha por defecto y el mes en curso se deciden en la hora de Argentina (TimeZoneConfiguration).
    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));

    private UUID casa;
    private UUID ana;
    private UUID beto;
    private UUID supermercado;
    private UUID nafta;

    private UUID otraCasa;
    private UUID carla;
    private UUID deOtroHogar;
    private UUID gastoDeOtroHogar;

    @BeforeEach
    void sembrar() {
        casa = createHousehold("Casa de Ana");
        ana = createUser(casa, uniqueEmail("ana"), "Ana");
        beto = createUser(casa, uniqueEmail("beto"), "Beto");
        supermercado = category(casa, "Supermercado");
        nafta = category(casa, "Nafta");

        otraCasa = createHousehold("Casa de Carla");
        carla = createUser(otraCasa, uniqueEmail("carla"), "Carla");
        deOtroHogar = category(otraCasa, "Supermercado");
        gastoDeOtroHogar = expense(otraCasa, carla, deOtroHogar, "Carrefour", "77777.00", SEPTIEMBRE, "Tarjeta");
    }

    // Primero la casa: si alguien se cambió a la otra, sus gastos quedaron acá y hay que borrarlos
    // antes que a la persona.
    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
        deleteHousehold(otraCasa);
    }

    // ── Alta (HU-08) ────────────────────────────────────────────────────────

    // RF-13, RF-14: con importe y comercio alcanza. Fecha de hoy, quien carga como responsable, sin
    // categoría y en efectivo.
    @Test
    void elAltaMinimaCompletaFechaResponsableYMedioDePago() throws Exception {
        String body = create(ana, """
                {"amount": "1234.56", "merchant": "Carrefour"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.amount").value("1234.56"))
                .andExpect(jsonPath("$.merchant").value("Carrefour"))
                .andExpect(jsonPath("$.expenseDate").value(HOY.toString()))
                .andExpect(jsonPath("$.paymentMethod").value("Efectivo"))
                .andExpect(jsonPath("$.categoryId", nullValue()))
                .andExpect(jsonPath("$.categoryName", nullValue()))
                .andExpect(jsonPath("$.ownerId").value(ana.toString()))
                .andExpect(jsonPath("$.ownerName").value("Ana"))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> row = row(casa, UUID.fromString(JsonPath.read(body, "$.id")));
        assertThat(row.get("household_id")).isEqualTo(casa);
        assertThat(row.get("owner_id")).isEqualTo(ana);
        assertThat(row.get("amount")).isEqualTo(new BigDecimal("1234.56"));
    }

    @Test
    void elAltaAceptaFechaMedioDePagoYCategoria() throws Exception {
        create(ana, """
                {"amount": "2000", "merchant": "Shell", "expenseDate": "2026-09-10",
                 "paymentMethod": "Tarjeta", "categoryId": "%s"}""".formatted(nafta))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("2000.00"))
                .andExpect(jsonPath("$.expenseDate").value("2026-09-10"))
                .andExpect(jsonPath("$.paymentMethod").value("Tarjeta"))
                .andExpect(jsonPath("$.categoryId").value(nafta.toString()))
                .andExpect(jsonPath("$.categoryName").value("Nafta"));
    }

    @Test
    void elComercioSeGuardaSinLosEspaciosDeLosExtremos() throws Exception {
        create(ana, """
                {"amount": "10.00", "merchant": "  Kiosco  "}""")
                .andExpect(jsonPath("$.merchant").value("Kiosco"));
    }

    // RF-14: el responsable sale de la sesión. Un ownerId en el cuerpo no se lee.
    @Test
    void elCuerpoNoPuedeElegirElResponsable() throws Exception {
        create(ana, """
                {"amount": "10.00", "merchant": "Kiosco", "ownerId": "%s"}""".formatted(beto))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(ana.toString()));
    }

    // RN-06 y RN-07. El alta desde la interfaz recibe el importe con punto decimal (RD-01): el formato
    // local lo normaliza solo la captura de M6.
    @Test
    void rechazaImportesYComerciosInvalidos() throws Exception {
        for (String body : new String[] {
                """
                {"amount": "0", "merchant": "Kiosco"}""",
                """
                {"amount": "-5.00", "merchant": "Kiosco"}""",
                """
                {"amount": "10.999", "merchant": "Kiosco"}""",
                """
                {"amount": "1.234,56", "merchant": "Kiosco"}""",
                """
                {"amount": "10.00", "merchant": "   "}""",
                """
                {"merchant": "Kiosco"}""",
                """
                {"amount": "10.00", "merchant": "Kiosco", "paymentMethod": "Cheque"}"""}) {
            create(ana, body).andExpect(status().isBadRequest());
        }
        assertThat(activeExpenses(casa)).isZero();
    }

    // RN-08, RN-16: una categoría de otro hogar o dada de baja no se asigna, y la respuesta es la misma.
    @Test
    void rechazaUnaCategoriaDeOtroHogarODadaDeBaja() throws Exception {
        deactivate(nafta);

        for (UUID categoria : new UUID[] {deOtroHogar, nafta, UUID.randomUUID()}) {
            create(ana, """
                    {"amount": "10.00", "merchant": "Kiosco", "categoryId": "%s"}""".formatted(categoria))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("La categoría no existe o está dada de baja."));
        }
        assertThat(activeExpenses(casa)).isZero();
    }

    // ── Detalle ─────────────────────────────────────────────────────────────

    @Test
    void elDetalleDevuelveElGastoConLosNombres() throws Exception {
        UUID gasto = expense(casa, beto, supermercado, "Día", "500.50", SEPTIEMBRE, "Efectivo");

        mockMvc.perform(authorized(get("/api/expenses/{id}", gasto), ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("500.50"))
                .andExpect(jsonPath("$.categoryName").value("Supermercado"))
                .andExpect(jsonPath("$.ownerName").value("Beto"));
    }

    // RN-16: los gastos viejos conservan su categoría dada de baja, con su nombre.
    @Test
    void unGastoConservaSuCategoriaDadaDeBaja() throws Exception {
        UUID gasto = expense(casa, ana, nafta, "Shell", "2000.00", SEPTIEMBRE, "Tarjeta");
        deactivate(nafta);

        mockMvc.perform(authorized(get("/api/expenses/{id}", gasto), ana))
                .andExpect(jsonPath("$.categoryName").value("Nafta"));
    }

    @Test
    void unIdentificadorMalFormadoEsUnErrorDelCliente() throws Exception {
        mockMvc.perform(authorized(get("/api/expenses/no-es-un-uuid"), ana))
                .andExpect(status().isBadRequest());
    }

    // ── Edición (HU-09) ─────────────────────────────────────────────────────

    // RF-15: lo que no viene no se toca.
    @Test
    void laEdicionCambiaSoloLosCamposQueVienen() throws Exception {
        UUID gasto = expense(casa, ana, supermercado, "Carrefour", "1000.00", SEPTIEMBRE, "Tarjeta");

        update(ana, gasto, """
                {"amount": "1100.5"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("1100.50"))
                .andExpect(jsonPath("$.merchant").value("Carrefour"))
                .andExpect(jsonPath("$.paymentMethod").value("Tarjeta"))
                .andExpect(jsonPath("$.categoryId").value(supermercado.toString()))
                .andExpect(jsonPath("$.ownerId").value(ana.toString()));

        assertThat(row(casa, gasto).get("amount")).isEqualTo(new BigDecimal("1100.50"));
    }

    @Test
    void laEdicionCambiaTodosLosCamposEditables() throws Exception {
        UUID gasto = expense(casa, ana, supermercado, "Carrefour", "1000.00", SEPTIEMBRE, "Tarjeta");

        update(ana, gasto, """
                {"amount": "20.00", "merchant": " Shell ", "expenseDate": "2026-08-31",
                 "paymentMethod": "Transferencia", "categoryId": "%s"}""".formatted(nafta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("20.00"))
                .andExpect(jsonPath("$.merchant").value("Shell"))
                .andExpect(jsonPath("$.expenseDate").value("2026-08-31"))
                .andExpect(jsonPath("$.paymentMethod").value("Transferencia"))
                .andExpect(jsonPath("$.categoryName").value("Nafta"));
    }

    // HU-09: cualquier integrante corrige gastos de otro; el responsable no cambia.
    @Test
    void unIntegranteEditaElGastoDeOtro() throws Exception {
        UUID gasto = expense(casa, ana, supermercado, "Carrefour", "1000.00", SEPTIEMBRE, "Tarjeta");

        update(beto, gasto, """
                {"merchant": "Carrefour Express"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value(ana.toString()));
    }

    // RN-18: el gasto de quien se fue del hogar sigue siendo del hogar, y se puede corregir. Si la
    // edición reescribiera el responsable, el trigger de la V5 la rechazaría.
    @Test
    void seEditaElGastoDeQuienSeFueDelHogar() throws Exception {
        UUID gasto = expense(casa, beto, supermercado, "Día", "500.50", SEPTIEMBRE, "Efectivo");
        jdbcTemplate.update("update users set household_id = ? where id = ?", otraCasa, beto);

        update(ana, gasto, """
                {"amount": "600.00"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value("600.00"))
                .andExpect(jsonPath("$.ownerId").value(beto.toString()))
                .andExpect(jsonPath("$.ownerName").value("Beto"));
    }

    // RN-12: un null en categoryId es "no cambiar"; la bandeja se pide explícitamente.
    @Test
    void laEdicionDevuelveElGastoALaBandeja() throws Exception {
        UUID gasto = expense(casa, ana, supermercado, "Carrefour", "1000.00", SEPTIEMBRE, "Tarjeta");

        update(ana, gasto, """
                {"categoryId": null}""")
                .andExpect(jsonPath("$.categoryId").value(supermercado.toString()));
        update(ana, gasto, """
                {"uncategorized": true}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId", nullValue()))
                .andExpect(jsonPath("$.categoryName", nullValue()));
        update(ana, gasto, """
                {"uncategorized": true, "categoryId": "%s"}""".formatted(nafta))
                .andExpect(status().isBadRequest());
    }

    // RN-16: una categoría dada de baja no se asigna. Pero reenviar la que el gasto ya tiene no es
    // asignarla: un formulario completo tiene que poder corregir el importe de un gasto viejo.
    @Test
    void laEdicionNoAsignaUnaCategoriaDadaDeBajaPeroLaConserva() throws Exception {
        UUID gasto = expense(casa, ana, nafta, "Shell", "2000.00", SEPTIEMBRE, "Tarjeta");
        UUID otro = expense(casa, ana, supermercado, "Carrefour", "1000.00", SEPTIEMBRE, "Tarjeta");
        deactivate(nafta);

        update(ana, otro, """
                {"categoryId": "%s"}""".formatted(nafta))
                .andExpect(status().isBadRequest());
        update(ana, otro, """
                {"categoryId": "%s"}""".formatted(deOtroHogar))
                .andExpect(status().isBadRequest());
        update(ana, gasto, """
                {"amount": "2100.00", "categoryId": "%s"}""".formatted(nafta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Nafta"));
    }

    @Test
    void laEdicionValidaComoElAlta() throws Exception {
        UUID gasto = expense(casa, ana, supermercado, "Carrefour", "1000.00", SEPTIEMBRE, "Tarjeta");

        update(ana, gasto, """
                {"amount": "0"}""").andExpect(status().isBadRequest());
        update(ana, gasto, """
                {"merchant": "  "}""").andExpect(status().isBadRequest());
        assertThat(row(casa, gasto).get("amount")).isEqualTo(new BigDecimal("1000.00"));
    }

    // ── Baja (HU-09, RN-16) ─────────────────────────────────────────────────

    @Test
    void laBajaEsLogicaYElGastoDejaDeAparecer() throws Exception {
        UUID gasto = expense(casa, ana, supermercado, "Carrefour", "1000.00", SEPTIEMBRE, "Tarjeta");

        mockMvc.perform(authorized(delete("/api/expenses/{id}", gasto), beto))
                .andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/expenses/{id}", gasto), ana)).andExpect(status().isNotFound());
        update(ana, gasto, """
                {"amount": "1.00"}""").andExpect(status().isNotFound());
        mockMvc.perform(authorized(delete("/api/expenses/{id}", gasto), ana)).andExpect(status().isNotFound());
        list(ana, septiembre()).andExpect(jsonPath("$.totalElements").value(0));

        // La fila se conserva, con su importe, su comercio y su responsable.
        Map<String, Object> row = row(casa, gasto);
        assertThat(row.get("active")).isEqualTo(false);
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("amount")).isEqualTo(new BigDecimal("1000.00"));
        assertThat(row.get("merchant")).isEqualTo("Carrefour");
        assertThat(row.get("owner_id")).isEqualTo(ana);
    }

    // ── Gastos de otro hogar (HU-09) ────────────────────────────────────────

    // Conocer el identificador no alcanza: el gasto de otro hogar responde como uno inexistente, y
    // queda como estaba.
    @Test
    void unGastoDeOtroHogarRespondeComoInexistente() throws Exception {
        mockMvc.perform(authorized(get("/api/expenses/{id}", gastoDeOtroHogar), ana))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe ese gasto."));
        update(ana, gastoDeOtroHogar, """
                {"amount": "1.00", "merchant": "Hackeado"}""")
                .andExpect(status().isNotFound());
        mockMvc.perform(authorized(delete("/api/expenses/{id}", gastoDeOtroHogar), ana))
                .andExpect(status().isNotFound());

        Map<String, Object> row = row(otraCasa, gastoDeOtroHogar);
        assertThat(row.get("active")).isEqualTo(true);
        assertThat(row.get("amount")).isEqualTo(new BigDecimal("77777.00"));
        assertThat(row.get("merchant")).isEqualTo("Carrefour");
    }

    @Test
    void unGastoInexistenteDa404() throws Exception {
        mockMvc.perform(authorized(get("/api/expenses/{id}", UUID.randomUUID()), ana))
                .andExpect(status().isNotFound());
    }

    // ── Listado (HU-10) ─────────────────────────────────────────────────────

    // RF-17: por fecha descendente y de a una página. El gasto de la otra casa, del mismo mes, no está.
    @Test
    void elListadoVaPorFechaDescendenteYPaginado() throws Exception {
        expense(casa, ana, supermercado, "Primero", "1.00", LocalDate.of(2026, 9, 1), "Tarjeta");
        expense(casa, ana, supermercado, "Tercero", "3.00", LocalDate.of(2026, 9, 20), "Tarjeta");
        expense(casa, ana, supermercado, "Segundo", "2.00", LocalDate.of(2026, 9, 10), "Tarjeta");

        list(ana, septiembre().param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].merchant", contains("Tercero", "Segundo")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        list(ana, septiembre().param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content[*].merchant", contains("Primero")));
        list(ana, septiembre().param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // Muchos gastos comparten día: el orden tiene que ser estable para que recorrer las páginas los
    // traiga a todos, una sola vez.
    @Test
    void lasPaginasNoRepitenNiPierdenGastosDelMismoDia() throws Exception {
        for (int i = 0; i < 7; i++) {
            expense(casa, ana, supermercado, "Gasto " + i, "1.00", SEPTIEMBRE, "Tarjeta");
        }

        Set<String> vistos = new HashSet<>();
        for (int page = 0; page < 4; page++) {
            String body = list(ana, septiembre().param("size", "2").param("page", String.valueOf(page)))
                    .andReturn().getResponse().getContentAsString();
            List<String> ids = JsonPath.read(body, "$.content[*].id");
            assertThat(vistos.addAll(ids)).isTrue();
        }
        assertThat(vistos).hasSize(7);
    }

    // RD-07: sin período, el mes en curso.
    @Test
    void sinPeriodoElListadoTomaElMesEnCurso() throws Exception {
        expense(casa, ana, supermercado, "Este mes", "1.00", HOY.withDayOfMonth(1), "Tarjeta");
        expense(casa, ana, supermercado, "Mes pasado", "1.00", HOY.withDayOfMonth(1).minusDays(1), "Tarjeta");

        list(ana, get("/api/expenses"))
                .andExpect(jsonPath("$.content[*].merchant", contains("Este mes")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    // HU-10: los filtros se combinan.
    @Test
    void losFiltrosSeCombinan() throws Exception {
        sembrarSeptiembre();

        list(ana, septiembre().param("categoryId", supermercado.toString()).param("paymentMethod", "Tarjeta"))
                .andExpect(jsonPath("$.content[*].merchant", contains("Carrefour")));
        list(ana, septiembre().param("ownerId", beto.toString()))
                .andExpect(jsonPath("$.content[*].merchant", contains("Kiosco", "Día")));
        list(ana, get("/api/expenses").param("from", "2026-09-15"))
                .andExpect(jsonPath("$.content[*].merchant", contains("Kiosco")));
    }

    // RF-22: la bandeja son los gastos sin categoría.
    @Test
    void laBandejaListaLosGastosSinCategoria() throws Exception {
        sembrarSeptiembre();

        list(ana, septiembre().param("categoryId", "uncategorized"))
                .andExpect(jsonPath("$.content[*].merchant", contains("Kiosco")))
                .andExpect(jsonPath("$.content[0].categoryId", nullValue()));
    }

    // Filtrar por una categoría o una persona de otro hogar no da error ni datos: no hay gastos que
    // coincidan, como en el análisis.
    @Test
    void filtrarPorAlgoDeOtroHogarDaUnaPaginaVacia() throws Exception {
        sembrarSeptiembre();

        list(ana, septiembre().param("categoryId", deOtroHogar.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        list(ana, septiembre().param("ownerId", carla.toString()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void rechazaFiltrosYPaginasInvalidos() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get("/api/expenses").param("size", "101"),
                get("/api/expenses").param("size", "0"),
                get("/api/expenses").param("page", "-1"),
                get("/api/expenses").param("page", "10001"),
                get("/api/expenses").param("from", "2026-09-30").param("to", "2026-09-01"),
                get("/api/expenses").param("from", "30/09/2026"),
                get("/api/expenses").param("categoryId", "foo"),
                get("/api/expenses").param("paymentMethod", "Cheque")}) {
            list(ana, request).andExpect(status().isBadRequest());
        }
    }

    // ── Autenticación ───────────────────────────────────────────────────────

    @Test
    void sinSesionNadaResponde() throws Exception {
        UUID gasto = expense(casa, ana, supermercado, "Carrefour", "1000.00", SEPTIEMBRE, "Tarjeta");

        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get("/api/expenses"),
                get("/api/expenses/{id}", gasto),
                post("/api/expenses").contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": "1.00", "merchant": "Kiosco"}"""),
                patch("/api/expenses/{id}", gasto).contentType(MediaType.APPLICATION_JSON).content("""
                        {"amount": "1.00"}"""),
                delete("/api/expenses/{id}", gasto)}) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        assertThat(row(casa, gasto).get("active")).isEqualTo(true);
        assertThat(activeExpenses(casa)).isEqualTo(1);
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────

    private void sembrarSeptiembre() {
        expense(casa, ana, supermercado, "Carrefour", "1000.00", LocalDate.of(2026, 9, 12), "Tarjeta");
        expense(casa, beto, supermercado, "Día", "500.50", LocalDate.of(2026, 9, 11), "Efectivo");
        expense(casa, ana, nafta, "Shell", "2000.00", LocalDate.of(2026, 9, 10), "Tarjeta");
        expense(casa, beto, null, "Kiosco", "300.00", LocalDate.of(2026, 9, 20), "Transferencia");
    }

    private MockHttpServletRequestBuilder septiembre() {
        return get("/api/expenses").param("from", "2026-09-01").param("to", "2026-09-30");
    }

    private ResultActions list(UUID user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(authorized(request, user));
    }

    private ResultActions create(UUID user, String body) throws Exception {
        return mockMvc.perform(authorized(post("/api/expenses"), user)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions update(UUID user, UUID expense, String body) throws Exception {
        return mockMvc.perform(authorized(patch("/api/expenses/{id}", expense), user)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder request, UUID user) {
        return request.header(AUTHORIZATION, bearer(user));
    }

    private UUID category(UUID household, String name) {
        UUID id = UUID.randomUUID();
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into categories (id, household_id, name) values (?, ?, ?)", id, household, name));
        return id;
    }

    private void deactivate(UUID category) {
        withHousehold(casa, () -> jdbcTemplate.update(
                "update categories set active = false, deleted_at = now() where id = ?", category));
    }

    private UUID expense(UUID household, UUID owner, UUID category, String merchant, String amount,
                         LocalDate date, String paymentMethod) {
        UUID id = UUID.randomUUID();
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into expenses (id, household_id, owner_id, category_id, merchant, amount, expense_date,"
                        + " payment_method) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, household, owner, category, merchant, new BigDecimal(amount), date, paymentMethod));
        return id;
    }

    /** La fila tal como está en la base, dada de baja o no. Bajo RLS, se lee con su hogar en contexto. */
    private Map<String, Object> row(UUID household, UUID expense) {
        return withHousehold(household, () -> jdbcTemplate.queryForMap("select * from expenses where id = ?", expense));
    }

    private int activeExpenses(UUID household) {
        return withHousehold(household, () -> jdbcTemplate.queryForObject(
                "select count(*) from expenses where active", Integer.class));
    }
}
