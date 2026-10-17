package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.AuthTestSupport;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Límite de intentos de {@code POST /api/auth/login} (RNF-06). Los detalles del conteo los cubre
 * {@code LoginAttemptLimiterTest}; acá, la respuesta por HTTP.
 *
 * <p>Los contadores viven en memoria y el contexto de Spring se comparte entre pruebas, así que
 * cada prueba usa su propia dirección: si no, sus intentos fallidos se sumarían a los de las demás.
 */
class LoginLimitTest extends AuthTestSupport {

    private static final String PASSWORD = "contraseña-de-prueba";
    private static final String BLOQUEADO = "Demasiados intentos fallidos. Esperá unos minutos y volvé a intentar.";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    private UUID casa;
    private String email;
    private String ip;

    @BeforeEach
    void sembrarUsuarioVerificado() {
        casa = createHousehold("Casa de Ana");
        email = uniqueEmail("ana");
        createVerifiedUser(casa, email, "Ana", PASSWORD);
        ip = "203.0.113." + NEXT_IP.getAndIncrement();
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
    }

    // Bloqueado, ni la contraseña correcta entra: si entrara, el bloqueo le avisaría a quien
    // prueba contraseñas en qué intento acertó.
    @Test
    void despuesDeDiezFallosRechazaInclusoLaContrasenaCorrecta() throws Exception {
        fallar(email, ip, 10);

        login(email, PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(BLOQUEADO))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")));
    }

    // CU-02, 2a.3: un correo sin cuenta se bloquea igual, así que el bloqueo no revela quién está
    // registrado.
    @Test
    void unCorreoSinCuentaSeBloqueaIgual() throws Exception {
        String nadie = uniqueEmail("nadie");
        fallar(nadie, ip, 10);

        login(nadie, PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value(BLOQUEADO));
    }

    @Test
    void elDuenoEntraDesdeOtraDireccion() throws Exception {
        fallar(email, ip, 10);

        login(email, PASSWORD, "198.51.100." + NEXT_IP.getAndIncrement())
                .andExpect(status().isOk());
    }

    private void fallar(String email, String ip, int veces) throws Exception {
        for (int i = 0; i < veces; i++) {
            login(email, "otra-contraseña", ip).andExpect(status().isUnauthorized());
        }
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}""".formatted(email, password)));
    }
}