package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.AuthTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Análisis: {@code /api/expenses/summary}, {@code /monthly} y {@code /merchants} (RF-25 a RF-29,
 * HU-13, HU-14, HU-19, HU-20, RN-14, RN-16, RN-18).
 *
 * <p>Los gastos se insertan directo en la base, con el hogar en contexto como exige RLS: el análisis
 * no depende de los endpoints de alta de gastos. Cada prueba siembra su propio hogar, y otro hogar
 * con gastos en las mismas fechas, que nunca tiene que aparecer en los totales.
 */
class AnalysisTest extends AuthTestSupport {

    private static final LocalDate SEPTIEMBRE = LocalDate.of(2026, 9, 10);

    // El mes en curso se decide en la hora de Argentina, como en AnalysisService.
    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));

    private UUID casa;
    private UUID ana;
    private UUID beto;
    private UUID supermercado;
    private UUID nafta;

    private UUID otraCasa;
    private UUID carla;
    private UUID deOtroHogar;

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
        expense(otraCasa, carla, deOtroHogar, "Carrefour", "77777.00", SEPTIEMBRE, "Tarjeta");
    }

    // Primero la casa: si alguien se cambió a la otra, sus gastos quedaron acá y hay que borrarlos
    // antes que a la persona.
    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
        deleteHousehold(otraCasa);
    }

    // ── Resumen ─────────────────────────────────────────────────────────────

    // HU-13: total y desgloses, solo del período y solo del hogar.
    @Test
    void elResumenDaElTotalYSusDesglosesDelPeriodo() throws Exception {
        expense(casa, ana, supermercado, "Carrefour", "1000.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, beto, supermercado, "Día", "500.50", SEPTIEMBRE, "Efectivo");
        expense(casa, ana, nafta, "Shell", "2000.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, beto, null, "Kiosco", "300.00", SEPTIEMBRE, "Transferencia");
        expense(casa, ana, supermercado, "Carrefour", "9999.00", LocalDate.of(2026, 8, 31), "Tarjeta");

        summary(ana, septiembre())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("3800.50"))
                .andExpect(jsonPath("$.byCategory[*].name", contains("Nafta", "Supermercado", null)))
                .andExpect(jsonPath("$.byCategory[*].total", contains("2000.00", "1500.50", "300.00")))
                .andExpect(jsonPath("$.byCategory[2].categoryId", nullValue()))
                .andExpect(jsonPath("$.byOwner[*].name", contains("Ana", "Beto")))
                .andExpect(jsonPath("$.byOwner[*].total", contains("3000.00", "800.50")))
                .andExpect(jsonPath("$.byPaymentMethod[*].paymentMethod", contains("Tarjeta", "Efectivo", "Transferencia")))
                .andExpect(jsonPath("$.byPaymentMethod[*].total", contains("3000.00", "500.50", "300.00")));
    }

    // HU-13 y RN-16: un gasto dado de baja no cuenta en ningún lado.
    @Test
    void losGastosDadosDeBajaNoCuentan() throws Exception {
        expense(casa, ana, supermercado, "Carrefour", "1000.00", SEPTIEMBRE, "Tarjeta");
        UUID dadoDeBaja = expense(casa, ana, nafta, "Shell", "5000.00", SEPTIEMBRE, "Tarjeta");
        withHousehold(casa, () -> jdbcTemplate.update(
                "update expenses set active = false, deleted_at = now() where id = ?", dadoDeBaja));

        summary(ana, septiembre())
                .andExpect(jsonPath("$.total").value("1000.00"))
                .andExpect(jsonPath("$.byCategory[*].name", contains("Supermercado")));
    }

    // Un período sin gastos no devuelve nulos: el total es "0.00" y las listas vienen vacías.
    @Test
    void unPeriodoSinGastosDaCeroYListasVacias() throws Exception {
        summary(ana, septiembre())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("0.00"))
                .andExpect(jsonPath("$.byCategory", hasSize(0)))
                .andExpect(jsonPath("$.byOwner", hasSize(0)))
                .andExpect(jsonPath("$.byPaymentMethod", hasSize(0)));
    }

    // RD-07, CU-06: sin período, el tablero muestra el mes en curso, no toda la historia.
    @Test
    void sinPeriodoElResumenYElRankingTomanElMesEnCurso() throws Exception {
        expense(casa, ana, supermercado, "Carrefour", "100.00", HOY.withDayOfMonth(1), "Tarjeta");
        expense(casa, ana, supermercado, "Día", "999.00", HOY.withDayOfMonth(1).minusDays(1), "Tarjeta");

        summary(ana, get("/api/expenses/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("100.00"));
        mockMvc.perform(authorized(get("/api/expenses/merchants"), ana))
                .andExpect(jsonPath("$[*].merchant", contains("Carrefour")));
    }

    // El total sale del desglose por medio de pago: los dos coinciden siempre.
    @Test
    void elTotalCoincideConLosDesgloses() throws Exception {
        sembrarSeptiembre();

        summary(ana, septiembre())
                .andExpect(jsonPath("$.total").value("3800.50"))
                .andExpect(jsonPath("$.byPaymentMethod[*].total", contains("3000.00", "500.50", "300.00")));
    }

    // RN-16: dar de baja una categoría no borra el gasto que ya se hizo con ella.
    @Test
    void unaCategoriaDadaDeBajaSigueContandoEnSuPeriodo() throws Exception {
        expense(casa, ana, nafta, "Shell", "2000.00", SEPTIEMBRE, "Tarjeta");
        withHousehold(casa, () -> jdbcTemplate.update(
                "update categories set active = false, deleted_at = now() where id = ?", nafta));

        summary(ana, septiembre()).andExpect(jsonPath("$.byCategory[*].name", contains("Nafta")));
    }

    // RN-18: quien se cambió de hogar deja sus gastos en este, y siguen a su nombre.
    @Test
    void quienSeCambioDeHogarSigueApareciendoConSusGastos() throws Exception {
        expense(casa, beto, supermercado, "Día", "500.50", SEPTIEMBRE, "Efectivo");
        jdbcTemplate.update("update users set household_id = ? where id = ?", otraCasa, beto);

        summary(ana, septiembre())
                .andExpect(jsonPath("$.byOwner[*].name", contains("Beto")))
                .andExpect(jsonPath("$.total").value("500.50"));
    }

    // ── Filtros (HU-20) ─────────────────────────────────────────────────────

    @Test
    void elResumenFiltradoPorCategoriaSoloIncluyeEsaCategoria() throws Exception {
        sembrarSeptiembre();

        summary(ana, septiembre().param("categoryId", supermercado.toString()))
                .andExpect(jsonPath("$.total").value("1500.50"))
                .andExpect(jsonPath("$.byOwner[*].name", contains("Ana", "Beto")))
                .andExpect(jsonPath("$.byOwner[*].total", contains("1000.00", "500.50")));
    }

    @Test
    void laBandejaFiltraLosGastosSinCategoria() throws Exception {
        sembrarSeptiembre();

        summary(ana, septiembre().param("categoryId", "uncategorized"))
                .andExpect(jsonPath("$.total").value("300.00"));
    }

    @Test
    void losFiltrosSeCombinan() throws Exception {
        sembrarSeptiembre();

        summary(ana, septiembre().param("ownerId", ana.toString()).param("paymentMethod", "Tarjeta"))
                .andExpect(jsonPath("$.total").value("3000.00"))
                .andExpect(jsonPath("$.byOwner[*].name", contains("Ana")));
    }

    // Conocer el id de una categoría de otro hogar no sirve para ver sus gastos.
    @Test
    void filtrarPorUnaCategoriaDeOtroHogarDaUnPeriodoVacio() throws Exception {
        sembrarSeptiembre();

        summary(ana, septiembre().param("categoryId", deOtroHogar.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value("0.00"));
        summary(ana, septiembre().param("ownerId", carla.toString()))
                .andExpect(jsonPath("$.total").value("0.00"));
    }

    // ── Serie mensual (HU-14) ───────────────────────────────────────────────

    // RN-14: un punto por mes con gasto; mayo no tiene, y no cuenta como cero en la media. Septiembre
    // tiene gasto, pero de la otra casa.
    @Test
    void laSerieTieneUnPuntoPorMesConGastoYLaMediaSoloLosCuenta() throws Exception {
        expense(casa, ana, supermercado, "Carrefour", "60.00", LocalDate.of(2026, 3, 5), "Tarjeta");
        expense(casa, beto, supermercado, "Carrefour", "40.00", LocalDate.of(2026, 3, 31), "Efectivo");
        expense(casa, ana, nafta, "Shell", "300.00", LocalDate.of(2026, 4, 1), "Tarjeta");
        expense(casa, ana, nafta, "Shell", "200.00", LocalDate.of(2026, 6, 15), "Tarjeta");

        mockMvc.perform(get("/api/expenses/monthly").header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[*].month", contains("2026-03", "2026-04", "2026-06")))
                .andExpect(jsonPath("$.months[*].total", contains("100.00", "300.00", "200.00")))
                .andExpect(jsonPath("$.average").value("200.00"));
    }

    // Redondeo a centavos: 302 / 3 = 100,666...
    @Test
    void laMediaSeRedondeaACentavos() throws Exception {
        expense(casa, ana, supermercado, "Carrefour", "100.00", LocalDate.of(2026, 1, 10), "Tarjeta");
        expense(casa, ana, supermercado, "Carrefour", "101.00", LocalDate.of(2026, 2, 10), "Tarjeta");
        expense(casa, ana, supermercado, "Carrefour", "101.00", LocalDate.of(2026, 3, 10), "Tarjeta");

        mockMvc.perform(get("/api/expenses/monthly").header(AUTHORIZATION, bearer(ana)))
                .andExpect(jsonPath("$.average").value("100.67"));
    }

    // HU-14: la media es lo habitual. El mes en curso, que no terminó, aparece en la serie pero no
    // entra en la media, y mirar un solo mes no la convierte en ese mes.
    @Test
    void laMediaNoIncluyeElMesEnCursoNiDependeDelPeriodo() throws Exception {
        expense(casa, ana, supermercado, "Carrefour", "100.00", LocalDate.of(2026, 3, 10), "Tarjeta");
        expense(casa, ana, supermercado, "Carrefour", "300.00", LocalDate.of(2026, 4, 10), "Tarjeta");
        expense(casa, ana, supermercado, "Carrefour", "5.00", HOY, "Tarjeta");

        mockMvc.perform(authorized(get("/api/expenses/monthly"), ana))
                .andExpect(jsonPath("$.months", hasSize(3)))
                .andExpect(jsonPath("$.average").value("200.00"));
        mockMvc.perform(authorized(get("/api/expenses/monthly"), ana)
                        .param("from", "2026-04-01").param("to", "2026-04-30"))
                .andExpect(jsonPath("$.months[*].month", contains("2026-04")))
                .andExpect(jsonPath("$.average").value("200.00"));
    }

    // Los demás filtros sí cambian lo habitual: la media de una categoría es la de esa categoría.
    @Test
    void laMediaRespetaLosDemasFiltros() throws Exception {
        expense(casa, ana, supermercado, "Carrefour", "100.00", LocalDate.of(2026, 3, 10), "Tarjeta");
        expense(casa, ana, nafta, "Shell", "900.00", LocalDate.of(2026, 3, 10), "Tarjeta");

        mockMvc.perform(authorized(get("/api/expenses/monthly"), ana).param("categoryId", supermercado.toString()))
                .andExpect(jsonPath("$.average").value("100.00"));
    }

    @Test
    void unHogarSinGastosDaUnaSerieVaciaYMediaCero() throws Exception {
        mockMvc.perform(get("/api/expenses/monthly").header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months", hasSize(0)))
                .andExpect(jsonPath("$.average").value("0.00"));
    }

    // ── Comercios (HU-19) ───────────────────────────────────────────────────

    // El escenario de la HU-19, con el gasto dado de baja en Shell. "CARREFOUR " es el mismo
    // comercio que "Carrefour", y se muestra la forma que más se repite.
    @Test
    void elRankingOrdenaLosComerciosPorGastoAcumulado() throws Exception {
        expense(casa, ana, supermercado, "Carrefour", "15000.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, beto, supermercado, "Carrefour", "10000.00", SEPTIEMBRE, "Efectivo");
        expense(casa, ana, supermercado, "CARREFOUR ", "5000.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, ana, nafta, "Shell", "12000.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, ana, null, "Farmacity", "4000.00", SEPTIEMBRE, "Tarjeta");
        UUID dadoDeBaja = expense(casa, ana, nafta, "Shell", "20000.00", SEPTIEMBRE, "Tarjeta");
        withHousehold(casa, () -> jdbcTemplate.update(
                "update expenses set active = false, deleted_at = now() where id = ?", dadoDeBaja));

        mockMvc.perform(authorized(get("/api/expenses/merchants"), ana)
                        .param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].merchant", contains("Carrefour", "Shell", "Farmacity")))
                .andExpect(jsonPath("$[*].total", contains("30000.00", "12000.00", "4000.00")))
                .andExpect(jsonPath("$[*].expenses", contains(3, 1, 1)));
    }

    @Test
    void elRankingDevuelveHastaLaCantidadPedida() throws Exception {
        expense(casa, ana, supermercado, "Carrefour", "300.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, ana, nafta, "Shell", "200.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, ana, null, "Farmacity", "100.00", SEPTIEMBRE, "Tarjeta");

        mockMvc.perform(authorized(get("/api/expenses/merchants"), ana)
                        .param("from", "2026-09-01").param("to", "2026-09-30").param("limit", "2"))
                .andExpect(jsonPath("$[*].merchant", contains("Carrefour", "Shell")));
    }

    // trim() de Postgres solo recorta el espacio ASCII. Las notificaciones del banco traen espacios
    // duros, saltos de línea o espacios repetidos, y son el mismo comercio.
    @Test
    void elRankingUnificaLasVariantesDeEspacios() throws Exception {
        expense(casa, ana, supermercado, "Carrefour Express", "100.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, ana, supermercado, "Carrefour Express", "100.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, ana, supermercado, "carrefour  express\u00A0", "100.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, ana, supermercado, "Carrefour\tExpress\n", "100.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, ana, nafta, "Shell", "250.00", SEPTIEMBRE, "Tarjeta");

        mockMvc.perform(authorized(get("/api/expenses/merchants"), ana)
                        .param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(jsonPath("$[*].merchant", contains("Carrefour Express", "Shell")))
                .andExpect(jsonPath("$[0].total").value("400.00"))
                .andExpect(jsonPath("$[0].expenses").value(4));
    }

    // ── Autenticación ───────────────────────────────────────────────────────

    @Test
    void sinSesionNadaResponde() throws Exception {
        for (String path : new String[] {"/api/expenses/summary", "/api/expenses/monthly", "/api/expenses/merchants"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────

    private void sembrarSeptiembre() {
        expense(casa, ana, supermercado, "Carrefour", "1000.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, beto, supermercado, "Día", "500.50", SEPTIEMBRE, "Efectivo");
        expense(casa, ana, nafta, "Shell", "2000.00", SEPTIEMBRE, "Tarjeta");
        expense(casa, beto, null, "Kiosco", "300.00", SEPTIEMBRE, "Transferencia");
    }

    private MockHttpServletRequestBuilder septiembre() {
        return get("/api/expenses/summary").param("from", "2026-09-01").param("to", "2026-09-30");
    }

    private ResultActions summary(UUID user, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(authorized(request, user));
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

    private UUID expense(UUID household, UUID owner, UUID category, String merchant, String amount,
                         LocalDate date, String paymentMethod) {
        UUID id = UUID.randomUUID();
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into expenses (id, household_id, owner_id, category_id, merchant, amount, expense_date,"
                        + " payment_method) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, household, owner, category, merchant, new BigDecimal(amount), date, paymentMethod));
        return id;
    }
}
