package io.github.patorinaldi.gastos.api.web;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import io.github.patorinaldi.gastos.api.config.RegistrationLimitProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Alta de cuenta por HTTP (RF-01, RF-02, CU-01, HU-01, RNF-31).
 *
 * <p>Lo que importa no es solo que responda 201, sino que el alta deje las tres cosas que RN-11
 * exige juntas —usuario, hogar y catálogo—. Que no deje nada cuando falla, y la carrera entre dos
 * altas, se prueban sobre el servicio en {@code RegistrationServiceTest}: por HTTP, la comprobación
 * previa del correo frena el segundo intento antes de que haya nada que revertir.
 */
class RegisterTest extends AuthTestSupport {

    private static final String CONTRASENA = "contrasena-de-prueba";

    @Autowired
    private RegistrationLimitProperties limits;

    private final List<UUID> households = new ArrayList<>();

    @AfterEach
    void limpiar() {
        households.forEach(this::deleteHousehold);
    }

    @Test
    void elAltaCreaLaCuentaSuHogarYSuCatalogo() throws Exception {
        String email = uniqueEmail("ana");

        String body = register("Ana", email, CONTRASENA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andReturn().getResponse().getContentAsString();

        UUID userId = UUID.fromString(JsonPath.read(body, "$.userId"));
        UUID householdId = recordHousehold(UUID.fromString(JsonPath.read(body, "$.householdId")));

        Map<String, Object> user = jdbcTemplate.queryForMap(
                "select email, email_verified, household_id, password_hash from users where id = ?", userId);
        assertThat(user)
                .containsEntry("email", email)
                .containsEntry("email_verified", false)
                .containsEntry("household_id", householdId);
        // La contraseña se guarda cifrada, nunca en claro (RNF-05). El prefijo es el de bcrypt.
        assertThat((String) user.get("password_hash")).isNotEqualTo(CONTRASENA).startsWith("$2");

        assertThat(jdbcTemplate.queryForObject(
                "select name from households where id = ?", String.class, householdId))
                .isEqualTo("Casa de Ana");

        // El catálogo inicial queda sembrado en el hogar nuevo, bajo sus propias políticas.
        assertThat(categoryCount(householdId, "Supermercado")).isEqualTo(1);
        assertThat(ruleCount(householdId)).isPositive();
    }

    // El índice único no distingue mayúsculas, así que no hace falta guardar el correo en
    // minúsculas, y se le devuelve a la persona como lo escribió.
    @Test
    void elCorreoSeGuardaComoLoEscribioLaPersona() throws Exception {
        String email = "Ana-" + UUID.randomUUID() + "@Ejemplo.TEST";

        UUID userId = registerAndRecord("Ana", email);

        assertThat(jdbcTemplate.queryForObject("select email from users where id = ?", String.class, userId))
                .isEqualTo(email);
    }

    // RN-02: el alta no habilita la cuenta. El 403 también comprueba que el hash guardado sirve,
    // porque la contraseña ya se validó para llegar hasta ahí.
    @Test
    void laCuentaNaceSinVerificarYRecienDespuesPuedeIniciarSesion() throws Exception {
        String email = uniqueEmail("ana");
        UUID userId = registerAndRecord("Ana", email);

        login(email, CONTRASENA).andExpect(status().isForbidden());

        jdbcTemplate.update("update users set email_verified = true where id = ?", userId);
        login(email, CONTRASENA).andExpect(status().isOk());
    }

    @Test
    void unCorreoYaRegistradoResponde409SinDistinguirMayusculas() throws Exception {
        String email = uniqueEmail("ana");
        registerAndRecord("Ana", email);

        register("Otra Ana", email.toUpperCase(), CONTRASENA)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void unaContrasenaCortaResponde400() throws Exception {
        register("Ana", uniqueEmail("ana"), "corta").andExpect(status().isBadRequest());
    }

    @Test
    void unCorreoInvalidoResponde400() throws Exception {
        register("Ana", "ana-arroba-nada", CONTRASENA).andExpect(status().isBadRequest());
    }

    // Postgres no puede guardar el carácter nulo en un texto. Sin la validación del nombre, llegaría
    // al insert y volvería como error interno. El escape va doblado: lo que viaja en el JSON es la
    // secuencia \u0000, que Jackson convierte en el carácter.
    @Test
    void unNombreConCaracteresDeControlResponde400() throws Exception {
        register("Ana\\u0000", uniqueEmail("ana"), CONTRASENA).andExpect(status().isBadRequest());
    }

    /**
     * RNF-31: superado el tope por dirección, el alta se rechaza con 429 y sin tocar la base. Cuentan
     * todos los intentos, también los que terminan en 409: lo que se limita es el sondeo, y repetir un
     * correo conocido es la forma de sondear.
     */
    @Test
    void superadoElTopeDeAltasPorDireccionRespondeTooManyRequests() throws Exception {
        RequestPostProcessor mismaDireccion = fromNewAddress();
        String email = uniqueEmail("ana");
        String body = register("Ana", email, CONTRASENA, mismaDireccion)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        recordHousehold(UUID.fromString(JsonPath.read(body, "$.householdId")));

        // El primero ya contó: el resto del tope se agota con correos repetidos, que no corren bcrypt.
        for (int intento = 2; intento <= limits.maxPerIp(); intento++) {
            register("Ana", email, CONTRASENA, mismaDireccion).andExpect(status().isConflict());
        }

        String otroCorreo = uniqueEmail("beto");
        register("Beto", otroCorreo, CONTRASENA, mismaDireccion)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(RETRY_AFTER));
        // El alta rechazada por el límite no creó nada.
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from users where upper(email) = upper(?)", Integer.class, otroCorreo))
                .isZero();
    }

    // ------------------------------------------------------------------

    private ResultActions register(String name, String email, String password) throws Exception {
        return register(name, email, password, fromNewAddress());
    }

    private ResultActions register(String name, String email, String password, RequestPostProcessor from)
            throws Exception {
        return mockMvc.perform(registerRequest(name, email, password).with(from));
    }

    private MockHttpServletRequestBuilder registerRequest(String name, String email, String password) {
        return post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "email": "%s", "password": "%s"}""".formatted(name, email, password));
    }

    /** Da de alta la cuenta, anota su hogar para la limpieza y devuelve el id del usuario. */
    private UUID registerAndRecord(String name, String email) throws Exception {
        String body = register(name, email, CONTRASENA)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        recordHousehold(UUID.fromString(JsonPath.read(body, "$.householdId")));
        return UUID.fromString(JsonPath.read(body, "$.userId"));
    }

    private UUID recordHousehold(UUID householdId) {
        households.add(householdId);
        return householdId;
    }

    private Integer categoryCount(UUID householdId, String name) {
        return withHousehold(householdId, () -> jdbcTemplate.queryForObject(
                "select count(*) from categories where name = ?", Integer.class, name));
    }

    private Integer ruleCount(UUID householdId) {
        return withHousehold(householdId, () -> jdbcTemplate.queryForObject(
                "select count(*) from category_rules", Integer.class));
    }
}
