package io.github.patorinaldi.gastos.api.web;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Límite de intentos con códigos de invitación sobre {@code preview} y {@code redeem} (RNF-30). Los
 * detalles del conteo los cubre {@code InvitationAttemptLimiterTest}; acá, la respuesta por HTTP.
 *
 * <p>Los contadores viven en memoria y el contexto de Spring se comparte entre pruebas, así que
 * cada prueba usa su propia dirección: si no, sus intentos fallidos se sumarían a los de las demás.
 */
class InvitationLimitTest extends AuthTestSupport {

    private static final String BLOQUEADO = "Demasiados intentos fallidos. Esperá unos minutos y volvé a intentar.";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    private UUID casaDeAna;
    private UUID casaDeJuan;
    private UUID ana;
    private UUID juan;
    private String ip;

    @BeforeEach
    void sembrarDosHogares() {
        casaDeAna = createHousehold("Casa de Ana");
        ana = createUser(casaDeAna, uniqueEmail("ana"), "Ana");
        casaDeJuan = createHousehold("Casa de Juan");
        juan = createUser(casaDeJuan, uniqueEmail("juan"), "Juan");
        ip = "192.0.2." + NEXT_IP.getAndIncrement();
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casaDeAna);
        deleteHousehold(casaDeJuan);
    }

    // La vista previa y el canje cuentan juntos. Bloqueada, ni un código válido entra: si entrara,
    // el bloqueo le avisaría a quien tantea en qué intento acertó.
    @Test
    void despuesDeDiezCodigosInvalidosRechazaInclusoUnoValido() throws Exception {
        for (int i = 0; i < 5; i++) {
            vistaPrevia(ana, "GST-2222-222", ip).andExpect(status().isGone());
            canjear(ana, "GST-2222-222", ip).andExpect(status().isGone());
        }
        String valido = codigoDe(juan);

        vistaPrevia(ana, valido, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(BLOQUEADO))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+")));
        canjear(ana, valido, ip).andExpect(status().isTooManyRequests());

        assertThat(jdbcTemplate.queryForObject("select household_id from users where id = ?", UUID.class, ana))
                .isEqualTo(casaDeAna);
    }

    // El código propio responde 409 y no cuenta como un intento de adivinar.
    @Test
    void elCodigoDelHogarPropioNoBloquea() throws Exception {
        String propio = codigoDe(ana);
        for (int i = 0; i < 12; i++) {
            vistaPrevia(ana, propio, ip).andExpect(status().isConflict());
        }

        vistaPrevia(ana, codigoDe(juan), ip).andExpect(status().isOk());
    }

    private String codigoDe(UUID usuario) throws Exception {
        String body = mockMvc.perform(post("/api/household/invitations").header(AUTHORIZATION, bearer(usuario)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.code");
    }

    private ResultActions vistaPrevia(UUID usuario, String codigo, String ip) throws Exception {
        return mockMvc.perform(post("/api/household/invitations/preview")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .header(AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s"}""".formatted(codigo)));
    }

    private ResultActions canjear(UUID usuario, String codigo, String ip) throws Exception {
        return mockMvc.perform(post("/api/household/invitations/redeem")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .header(AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "%s", "confirm": true}""".formatted(codigo)));
    }
}
