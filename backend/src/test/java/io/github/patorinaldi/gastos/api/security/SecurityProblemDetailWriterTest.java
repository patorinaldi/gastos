package io.github.patorinaldi.gastos.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Si el cuerpo del error no se puede serializar, la cadena de seguridad igual responde un
 * {@code problem+json}: el mínimo, armado a mano. Un error de seguridad nunca sale sin cuerpo.
 */
class SecurityProblemDetailWriterTest {

    @Test
    void siLaSerializacionFallaRespondeElCuerpoMinimo() throws Exception {
        ObjectMapper queFalla = mock(ObjectMapper.class);
        when(queFalla.writeValueAsBytes(any())).thenThrow(mock(JacksonException.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        // Lo que ya fijó el handler de Spring antes de escribir el cuerpo.
        response.setHeader("WWW-Authenticate", "Bearer");

        new SecurityProblemDetailWriter(queFalla).write(request, response, HttpStatus.UNAUTHORIZED, "un detalle");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).isEqualTo("{\"status\":401,\"title\":\"Unauthorized\"}");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }
}
