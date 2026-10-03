package io.github.patorinaldi.gastos.api.web;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import io.github.patorinaldi.gastos.api.service.auth.AuthService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inicio de sesión: {@code POST /api/auth/login} (RF-04, RF-05, RN-02).
 */
class LoginTest extends AuthTestSupport {

    private static final String PASSWORD = "contraseña-de-prueba";
    private static final String CREDENCIALES_INCORRECTAS = "Correo o contraseña incorrectos";

    @Autowired
    private JwtDecoder jwtDecoder;

    private UUID casa;
    private UUID ana;
    private String email;

    @BeforeEach
    void sembrarUsuarioVerificado() {
        casa = createHousehold("Casa de Ana");
        email = uniqueEmail("ana");
        ana = createVerifiedUser(casa, email, "Ana", PASSWORD);
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
    }

    @Test
    void conCredencialesCorrectasEmiteUnTokenQueAbreLaSesion() throws Exception {
        String body = login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(ana.toString()));
    }

    // RF-05 pide el hogar en el token, y la decisión de 7 días de vigencia queda fijada acá.
    @Test
    void elTokenIdentificaAlUsuarioLlevaElHogarYVenceALosSieteDias() throws Exception {
        String body = login(email, PASSWORD).andReturn().getResponse().getContentAsString();
        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.token"));

        assertThat(jwt.getSubject()).isEqualTo(ana.toString());
        assertThat(jwt.getClaimAsString(AuthService.HOUSEHOLD_CLAIM)).isEqualTo(casa.toString());
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofDays(7));
        assertThat(Instant.parse(JsonPath.read(body, "$.expiresAt"))).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void elCorreoNoDistingueMayusculas() throws Exception {
        login(email.toUpperCase(), PASSWORD)
                .andExpect(status().isOk());
    }

    // Misma respuesta en los dos casos: distinguirlos permitiría averiguar quién tiene cuenta.
    @Test
    void unaContrasenaIncorrectaYUnCorreoSinCuentaRespondenIgual() throws Exception {
        login(email, "otra-contraseña")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(CREDENCIALES_INCORRECTAS));
        login(uniqueEmail("nadie"), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(CREDENCIALES_INCORRECTAS));
    }

    @Test
    void unCorreoSinVerificarNoPuedeIniciarSesion() throws Exception {
        jdbcTemplate.update("update users set email_verified = false where id = ?", ana);

        login(email, PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    // Si el 403 se respondiera antes de validar la contraseña, cualquiera podría averiguar si
    // una cuenta está verificada con solo conocer el correo.
    @Test
    void sinVerificarYConContrasenaIncorrectaRespondeComoCredencialesIncorrectas() throws Exception {
        jdbcTemplate.update("update users set email_verified = false where id = ?", ana);

        login(email, "otra-contraseña")
                .andExpect(status().isUnauthorized());
    }

    // bcrypt lanza una excepción con más de 72 bytes. 40 eñes son 40 caracteres pero 80 bytes.
    @Test
    void unaContrasenaDeMasDe72BytesNoEsUnErrorInterno() throws Exception {
        login(email, "ñ".repeat(40))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(CREDENCIALES_INCORRECTAS));
    }

    @Test
    void unCuerpoInvalidoResponde400() throws Exception {
        login("no-es-un-correo", PASSWORD)
                .andExpect(status().isBadRequest());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, password)));
    }
}
