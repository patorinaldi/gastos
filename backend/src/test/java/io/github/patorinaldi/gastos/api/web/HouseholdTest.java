package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.AuthTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El hogar del usuario autenticado: sus datos, sus integrantes y su nombre (HU-07, HU-18; RF-10,
 * RF-11).
 */
class HouseholdTest extends AuthTestSupport {

    private UUID casa;
    private UUID otraCasa;
    private UUID ana;
    private UUID bruno;
    private UUID carla;

    @BeforeEach
    void sembrarDosHogares() {
        casa = createHousehold("Casa de Ana");
        ana = createUser(casa, uniqueEmail("ana"), "Ana");
        carla = createUser(casa, uniqueEmail("carla"), "Carla");
        bruno = createUser(casa, uniqueEmail("bruno"), "Bruno");

        otraCasa = createHousehold("Casa de Diego");
        createUser(otraCasa, uniqueEmail("diego"), "Diego");
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
        deleteHousehold(otraCasa);
    }

    @Test
    void devuelveElHogarDelUsuarioAutenticado() throws Exception {
        Instant alta = jdbcTemplate.queryForObject(
                "select created_at from households where id = ?", Timestamp.class, casa).toInstant();

        mockMvc.perform(get("/api/household").header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(casa.toString()))
                .andExpect(jsonPath("$.name").value("Casa de Ana"))
                .andExpect(jsonPath("$.createdAt").value(alta.toString()));
    }

    @Test
    void sinSesionNoDevuelveNingunHogar() throws Exception {
        mockMvc.perform(get("/api/household"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listaALosIntegrantesDelHogarOrdenadosPorNombreYANadieDeOtro() throws Exception {
        mockMvc.perform(get("/api/household/members").header(AUTHORIZATION, bearer(carla)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(ana.toString()))
                .andExpect(jsonPath("$[0].name").value("Ana"))
                .andExpect(jsonPath("$[0].email").isString())
                .andExpect(jsonPath("$[1].name").value("Bruno"))
                .andExpect(jsonPath("$[2].name").value("Carla"));
    }

    @Test
    void elNuevoNombreLoVenTodosLosIntegrantes() throws Exception {
        renombrar(ana, "Depto Palermo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(casa.toString()))
                .andExpect(jsonPath("$.name").value("Depto Palermo"));

        mockMvc.perform(get("/api/household").header(AUTHORIZATION, bearer(bruno)))
                .andExpect(jsonPath("$.name").value("Depto Palermo"));
    }

    @Test
    void elNombreSeGuardaSinLosEspaciosDeLosExtremos() throws Exception {
        renombrar(ana, "  Depto Palermo  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Depto Palermo"));

        assertThat(nombreGuardado(casa)).isEqualTo("Depto Palermo");
    }

    @Test
    void unNombreSoloDeEspaciosSeRechazaYConservaElAnterior() throws Exception {
        renombrar(ana, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        assertThat(nombreGuardado(casa)).isEqualTo("Casa de Ana");
    }

    // Renombrar alcanza solo al hogar propio: el otro no se toca aunque se conozca su id, porque la
    // ruta no lleva ninguno.
    @Test
    void renombrarNoAlcanzaAOtroHogar() throws Exception {
        renombrar(ana, "Depto Palermo").andExpect(status().isOk());

        assertThat(nombreGuardado(otraCasa)).isEqualTo("Casa de Diego");
    }

    private ResultActions renombrar(UUID usuario, String nombre) throws Exception {
        return mockMvc.perform(patch("/api/household")
                .header(AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s"}""".formatted(nombre)));
    }

    private String nombreGuardado(UUID hogar) {
        return jdbcTemplate.queryForObject("select name from households where id = ?", String.class, hogar);
    }
}
