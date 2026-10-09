package io.github.patorinaldi.gastos.api.security;

import io.github.patorinaldi.gastos.api.AuthTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
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

    @Test
    void unaSesionSinPermisoRespondeProblemDetail403() throws Exception {
        mockMvc.perform(post("/api/capture").header(AUTHORIZATION, bearer(ana)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").value(ProblemDetailAccessDeniedHandler.DETAIL))
                .andExpect(jsonPath("$.instance").value("/api/capture"));
    }

    // Mismo formato que un error que responde un controlador, como el del login.
    @Test
    void elFormatoEsElMismoQueElDeLosControladores() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "nadie@ejemplo.test", "password": "una-contraseña"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/auth/login"));
    }
}