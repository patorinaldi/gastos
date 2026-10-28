package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.AuthTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Catálogo de categorías: {@code /api/categories} (RF-24, HU-12, RN-09, RN-16).
 *
 * <p>Cada prueba siembra su hogar y otro con una categoría del mismo nombre, que nunca tiene que
 * aparecer, chocar ni poder tocarse.
 */
class CategoryTest extends AuthTestSupport {

    private UUID casa;
    private UUID ana;
    private UUID supermercado;
    private UUID transporte;

    private UUID otraCasa;
    private UUID deOtroHogar;

    @BeforeEach
    void sembrar() {
        casa = createHousehold("Casa de Ana");
        ana = createUser(casa, uniqueEmail("ana"), "Ana");
        supermercado = category(casa, "Supermercado");
        transporte = category(casa, "Transporte");

        otraCasa = createHousehold("Casa de Carla");
        createUser(otraCasa, uniqueEmail("carla"), "Carla");
        deOtroHogar = category(otraCasa, "Viajes");
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
        deleteHousehold(otraCasa);
    }

    // ── Catálogo ────────────────────────────────────────────────────────────

    @Test
    void elCatalogoTieneLasActivasDelHogarPorNombre() throws Exception {
        UUID baja = category(casa, "Antigua");
        withHousehold(casa, () -> jdbcTemplate.update(
                "update categories set active = false, deleted_at = now() where id = ?", baja));

        mockMvc.perform(authorized(get("/api/categories")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Supermercado", "Transporte")))
                .andExpect(jsonPath("$[0].id").value(supermercado.toString()));
    }

    // ── Alta ────────────────────────────────────────────────────────────────

    @Test
    void creaUnaCategoriaSinLosEspaciosDeLosExtremos() throws Exception {
        save(post("/api/categories"), "  Mascotas  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Mascotas"));

        mockMvc.perform(authorized(get("/api/categories")))
                .andExpect(jsonPath("$[*].name", contains("Mascotas", "Supermercado", "Transporte")));
    }

    // HU-12, RN-09: sin distinguir mayúsculas.
    @Test
    void rechazaUnNombreRepetido() throws Exception {
        save(post("/api/categories"), "supermercado")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Ya existe una categoría con ese nombre."));
    }

    // Otro hogar puede tener una categoría con el mismo nombre: la unicidad es por hogar.
    @Test
    void elNombreDeOtroHogarNoChoca() throws Exception {
        save(post("/api/categories"), "Viajes").andExpect(status().isCreated());
    }

    // RN-09, RN-16: una dada de baja no ocupa su nombre.
    @Test
    void elNombreDeUnaDadaDeBajaQuedaLibre() throws Exception {
        mockMvc.perform(authorized(delete("/api/categories/{id}", transporte))).andExpect(status().isNoContent());

        save(post("/api/categories"), "Transporte").andExpect(status().isCreated());
    }

    @Test
    void rechazaNombresInvalidos() throws Exception {
        for (String name : new String[] {"", "   ", "  ", "x".repeat(201)}) {
            save(post("/api/categories"), name).andExpect(status().isBadRequest());
        }
        mockMvc.perform(authorized(post("/api/categories"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Hogar\\u0000\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── Renombrado ──────────────────────────────────────────────────────────

    // RN-16: renombrar no es dar de baja. El nombre nuevo se ve en los gastos de cualquier período.
    @Test
    void renombrarCambiaElNombreEnTodosLosGastos() throws Exception {
        UUID gasto = expense(casa, ana, supermercado, LocalDate.of(2026, 3, 10));

        save(patch("/api/categories/{id}", supermercado), "Súper")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Súper"));

        mockMvc.perform(authorized(get("/api/expenses/{id}", gasto)))
                .andExpect(jsonPath("$.categoryName").value("Súper"));
    }

    @Test
    void renombrarAlNombreDeOtraSeRechazaPeroCambiarMayusculasNo() throws Exception {
        save(patch("/api/categories/{id}", supermercado), "TRANSPORTE").andExpect(status().isConflict());
        save(patch("/api/categories/{id}", supermercado), "SUPERMERCADO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("SUPERMERCADO"));
    }

    @Test
    void noSeRenombraUnaCategoriaDadaDeBajaNiDeOtroHogar() throws Exception {
        mockMvc.perform(authorized(delete("/api/categories/{id}", transporte))).andExpect(status().isNoContent());

        save(patch("/api/categories/{id}", transporte), "Movilidad").andExpect(status().isNotFound());
        save(patch("/api/categories/{id}", deOtroHogar), "Hackeada")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe esa categoría."));
        assertThat(categoryRow(otraCasa, deOtroHogar).get("name")).isEqualTo("Viajes");
    }

    // ── Baja (HU-12, RN-16) ─────────────────────────────────────────────────

    // Deja de ofrecerse, sus reglas se eliminan y los gastos que ya la tenían la conservan.
    @Test
    void laBajaEsLogicaYLosGastosConservanLaCategoria() throws Exception {
        UUID gasto = expense(casa, ana, transporte, LocalDate.of(2026, 9, 10));
        rule(casa, "uber", transporte);

        mockMvc.perform(authorized(delete("/api/categories/{id}", transporte))).andExpect(status().isNoContent());

        mockMvc.perform(authorized(get("/api/categories"))).andExpect(jsonPath("$[*].name", contains("Supermercado")));
        mockMvc.perform(authorized(get("/api/category-rules"))).andExpect(jsonPath("$[*].pattern").isEmpty());
        mockMvc.perform(authorized(get("/api/expenses/{id}", gasto)))
                .andExpect(jsonPath("$.categoryId").value(transporte.toString()))
                .andExpect(jsonPath("$.categoryName").value("Transporte"));
        Map<String, Object> row = categoryRow(casa, transporte);
        assertThat(row.get("active")).isEqualTo(false);
        assertThat(row.get("deleted_at")).isNotNull();

        mockMvc.perform(authorized(delete("/api/categories/{id}", transporte))).andExpect(status().isNotFound());
    }

    @Test
    void noSeDaDeBajaUnaCategoriaDeOtroHogar() throws Exception {
        mockMvc.perform(authorized(delete("/api/categories/{id}", deOtroHogar))).andExpect(status().isNotFound());

        assertThat(categoryRow(otraCasa, deOtroHogar).get("active")).isEqualTo(true);
    }

    @Test
    void sinSesionNadaResponde() throws Exception {
        for (MockHttpServletRequestBuilder request : new MockHttpServletRequestBuilder[] {
                get("/api/categories"),
                post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"X\"}"),
                patch("/api/categories/{id}", supermercado).contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"X\"}"),
                delete("/api/categories/{id}", supermercado)}) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
        assertThat(categoryRow(casa, supermercado).get("name")).isEqualTo("Supermercado");
    }

    // ── Ayudantes ───────────────────────────────────────────────────────────

    private ResultActions save(MockHttpServletRequestBuilder request, String name) throws Exception {
        return mockMvc.perform(authorized(request).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\"}".formatted(name)));
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

    private void rule(UUID household, String pattern, UUID category) {
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into category_rules (household_id, pattern, category_id) values (?, ?, ?)",
                household, pattern, category));
    }

    private UUID expense(UUID household, UUID owner, UUID category, LocalDate date) {
        UUID id = UUID.randomUUID();
        withHousehold(household, () -> jdbcTemplate.update(
                "insert into expenses (id, household_id, owner_id, category_id, merchant, amount, expense_date,"
                        + " payment_method) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, household, owner, category, "Comercio", new BigDecimal("100.00"), date, "Tarjeta"));
        return id;
    }

    private Map<String, Object> categoryRow(UUID household, UUID category) {
        return withHousehold(household, () -> jdbcTemplate.queryForMap("select * from categories where id = ?", category));
    }
}
