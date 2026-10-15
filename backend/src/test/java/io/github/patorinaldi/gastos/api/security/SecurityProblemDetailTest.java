package io.github.patorinaldi.gastos.api.security;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los 401 y 403 que responde la cadena de seguridad, antes de llegar a un controlador, tienen el
 * mismo formato que el resto de los errores de la API: {@code application/problem+json} con
 * {@code status}, {@code detail} e {@code instance} (RF-40). Y conservan el header
 * {@code WWW-Authenticate}, que es lo que espera un cliente Bearer.
 */
class SecurityProblemDetailTest extends AuthTestSupport {

    @Autowired
    private JwtEncoder jwtEncoder;

    private UUID casa;
    private UUID ana;

    @BeforeEach
    void sembrar() {
        casa = createHousehold("Casa de Ana");
        ana = createUser(casa, uniqueEmail("ana"), "Ana");
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
    }

    @Test
    void sinTokenRespondeProblemDetail401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(ProblemDetailAuthenticationEntryPoint.DETAIL))
                .andExpect(jsonPath("$.instance").value("/api/auth/me"))
                .andExpect(header().string(WWW_AUTHENTICATE, startsWith("Bearer")));
    }

    // Lo rechaza el resource server, que usa su propio entry point: también tiene que salir con
    // el mismo formato, y con el error invalid_token en el header.
    @Test
    void unTokenVencidoRespondeProblemDetail401() throws Exception {
        String vencido = token(ana, Instant.now().minus(Duration.ofDays(8)));

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, "Bearer " + vencido))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(ProblemDetailAuthenticationEntryPoint.DETAIL))
                .andExpect(header().string(WWW_AUTHENTICATE, containsString("invalid_token")));
    }

    // Todos los caminos por los que una petición termina sin sesión responden lo mismo. Si el
    // cuerpo o el mensaje cambiaran según el motivo, un token viejo alcanzaría para averiguar el
    // estado de una cuenta. Cubre tanto lo que rechaza el decoder como lo que rechaza
    // SessionAuthenticationConverter después de validar la firma.
    @Test
    void todoRechazoDeSesionTieneElMismoCuerpo() throws Exception {
        UUID casaArchivada = createHousehold("Casa archivada");
        UUID beto = createUser(casaArchivada, uniqueEmail("beto"), "Beto");
        UUID carla = createUser(casa, uniqueEmail("carla"), "Carla");
        try {
            Map<String, String> casos = new LinkedHashMap<>();
            casos.put("sin token", null);
            casos.put("texto que no es un token", "Bearer no-es-un-token");
            casos.put("token vencido", "Bearer " + token(ana, Instant.now().minus(Duration.ofDays(8))));
            casos.put("usuario que no existe", bearer(UUID.randomUUID()));
            casos.put("hogar archivado", bearer(beto));
            casos.put("versión de sesión vieja", bearer(carla));
            casos.put("subject que no es un UUID", "Bearer " + tokenConSubject("no-es-un-uuid"));
            jdbcTemplate.update(
                    "update households set active = false, archived_at = now() where id = ?", casaArchivada);
            jdbcTemplate.update("update users set session_version = session_version + 1 where id = ?", carla);

            String esperado = null;
            for (Map.Entry<String, String> caso : casos.entrySet()) {
                var peticion = get("/api/auth/me");
                if (caso.getValue() != null) {
                    peticion.header(AUTHORIZATION, caso.getValue());
                }
                MvcResult resultado = mockMvc.perform(peticion)
                        .andExpect(status().isUnauthorized())
                        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                        .andExpect(jsonPath("$.detail").value(ProblemDetailAuthenticationEntryPoint.DETAIL))
                        .andExpect(header().string(WWW_AUTHENTICATE, startsWith("Bearer")))
                        .andReturn();

                String cuerpo = resultado.getResponse().getContentAsString();
                if (esperado == null) {
                    esperado = cuerpo;
                }
                assertThat(cuerpo).as(caso.getKey()).isEqualTo(esperado);
            }
        } finally {
            deleteHousehold(casaArchivada);
        }
    }

    @Test
    void unaSesionSinPermisoRespondeProblemDetail403() throws Exception {
        mockMvc.perform(post("/api/capture").header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(ProblemDetailAccessDeniedHandler.DETAIL))
                .andExpect(jsonPath("$.instance").value("/api/capture"));
    }

    // El formato no se negocia con el Accept de la petición: un navegador que pide HTML, o un
    // cliente que pide texto plano, recibe igual el problem+json y no una respuesta vacía.
    @Test
    void elFormatoNoDependeDelAcceptDeLaPeticion() throws Exception {
        for (String accept : new String[] {MediaType.TEXT_HTML_VALUE, MediaType.TEXT_PLAIN_VALUE}) {
            mockMvc.perform(get("/api/auth/me").header(ACCEPT, accept))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value(ProblemDetailAuthenticationEntryPoint.DETAIL));
            mockMvc.perform(post("/api/capture").header(AUTHORIZATION, bearer(ana)).header(ACCEPT, accept))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.detail").value(ProblemDetailAccessDeniedHandler.DETAIL));
        }
    }

    // La cadena escribe su cuerpo por su cuenta, sin pasar por Spring MVC. Esta prueba cuida que
    // no se desalinee del formato de los controladores: los mismos campos, ni uno más ni uno menos.
    @Test
    void tieneLosMismosCamposQueUnErrorDeControlador() throws Exception {
        String deControlador = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nadie@ejemplo.test", "password": "una-contraseña"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        String deLaCadena = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> controlador = JsonPath.read(deControlador, "$");
        Map<String, Object> cadena = JsonPath.read(deLaCadena, "$");
        assertThat(cadena.keySet()).isEqualTo(controlador.keySet());
        assertThat(cadena.get("status")).isEqualTo(controlador.get("status"));
        assertThat(cadena.get("title")).isEqualTo(controlador.get("title"));
    }

    // Firmado con la clave de la aplicación, pero con un subject que no identifica a un usuario.
    private String tokenConSubject(String subject) {
        Instant ahora = Instant.now();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder().subject(subject)
                        .issuedAt(ahora).expiresAt(ahora.plusSeconds(3600)).build()))
                .getTokenValue();
    }
}
