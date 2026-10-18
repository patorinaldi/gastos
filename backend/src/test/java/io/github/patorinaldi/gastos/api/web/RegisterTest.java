package io.github.patorinaldi.gastos.api.web;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import io.github.patorinaldi.gastos.api.service.auth.EmailAlreadyRegisteredException;
import io.github.patorinaldi.gastos.api.service.auth.Registration;
import io.github.patorinaldi.gastos.api.service.auth.RegistrationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Alta de cuenta (RF-01, RF-02, CU-01, HU-01).
 *
 * <p>Lo que importa no es solo que responda 201, sino que el alta deje las tres cosas que RN-11
 * exige juntas —usuario, hogar y catálogo— y ninguna de más cuando falla.
 */
class RegisterTest extends AuthTestSupport {

    private static final String CONTRASENA = "contrasena-de-prueba";

    @Autowired
    private RegistrationService registrationService;

    private final List<UUID> households = new ArrayList<>();
    private final ExecutorService hilos = Executors.newFixedThreadPool(2);

    @AfterEach
    void limpiar() {
        hilos.shutdownNow();
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

    // El alta rechazada no puede dejar el hogar creado: sería un hogar sin ningún integrante.
    @Test
    void unAltaRechazadaNoDejaHogarHuerfano() throws Exception {
        String email = uniqueEmail("ana");
        String nombre = "Ana " + UUID.randomUUID();
        registerAndRecord(nombre, email);

        register(nombre, email, CONTRASENA).andExpect(status().isConflict());

        assertThat(householdCount(nombre)).isEqualTo(1);
    }

    /**
     * Dos altas simultáneas con el mismo correo (RN-01). La comprobación previa no alcanza: las dos
     * pueden pasarla antes de que cualquiera inserte, y ahí decide el índice único. El resultado
     * tiene que ser el mismo por los dos caminos, así que la prueba no mira cuál actuó.
     */
    @Test
    void dosAltasSimultaneasConElMismoCorreoDejanUnaSolaCuenta() throws Exception {
        String email = uniqueEmail("ana");
        String nombre = "Ana " + UUID.randomUUID();

        Callable<Object> alta = () -> {
            try {
                return registrationService.register(nombre, email, CONTRASENA);
            } catch (EmailAlreadyRegisteredException rechazada) {
                return rechazada;
            }
        };
        Future<Object> primera = hilos.submit(alta);
        Future<Object> segunda = hilos.submit(alta);
        List<Object> resultados = List.of(primera.get(), segunda.get());

        List<Registration> altas = resultados.stream()
                .filter(Registration.class::isInstance)
                .map(Registration.class::cast)
                .toList();
        altas.forEach(registration -> recordHousehold(registration.householdId()));

        assertThat(altas).hasSize(1);
        assertThat(resultados).hasAtLeastOneElementOfType(EmailAlreadyRegisteredException.class);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from users where upper(email) = upper(?)", Integer.class, email))
                .isEqualTo(1);
        assertThat(householdCount(nombre)).isEqualTo(1);
    }

    @Test
    void unaContrasenaCortaResponde400() throws Exception {
        register("Ana", uniqueEmail("ana"), "corta").andExpect(status().isBadRequest());
    }

    @Test
    void unCorreoInvalidoResponde400() throws Exception {
        register("Ana", "ana-arroba-nada", CONTRASENA).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------

    private ResultActions register(String name, String email, String password) throws Exception {
        return mockMvc.perform(registerRequest(name, email, password));
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

    // households no tiene RLS: se cuenta por el nombre, que la prueba hace único.
    private Integer householdCount(String name) {
        return jdbcTemplate.queryForObject(
                "select count(*) from households where name = ?", Integer.class, "Casa de " + name);
    }
}
