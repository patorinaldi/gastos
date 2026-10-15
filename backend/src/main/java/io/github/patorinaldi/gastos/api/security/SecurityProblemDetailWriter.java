package io.github.patorinaldi.gastos.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Escribe el cuerpo {@code application/problem+json} de los errores que responde la cadena de
 * seguridad, antes de llegar a un controlador.
 *
 * <p>Lo escribe directamente, con el {@code ObjectMapper} de la aplicación, y no a través de Spring
 * MVC. MVC negocia el formato con el header {@code Accept} de la petición: con un
 * {@code Accept: text/html} no encontraría cómo escribir el error, y la respuesta saldría vacía o
 * con otro formato. Acá el formato no se negocia: un error de seguridad es siempre
 * {@code problem+json}. Que coincida con el de los controladores lo verifica
 * {@code SecurityProblemDetailTest}.
 *
 * <p>Si la serialización falla, queda registrado y se responde un cuerpo mínimo armado a mano: el
 * cliente nunca recibe un error de seguridad sin cuerpo.
 */
public class SecurityProblemDetailWriter {

    private static final Logger log = LoggerFactory.getLogger(SecurityProblemDetailWriter.class);

    private final ObjectMapper objectMapper;

    public SecurityProblemDetailWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Completa la respuesta con el estado y el cuerpo. No toca los headers que ya tenga, como el
     * {@code WWW-Authenticate} que fija el handler de Spring.
     */
    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(problem);
        } catch (JacksonException ex) {
            log.warn("No se pudo serializar el error {} de la cadena de seguridad; se responde el cuerpo mínimo",
                    status.value(), ex);
            body = fallback(status);
        }

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    // Sin texto variable, para que no haya nada que escapar a mano.
    private static byte[] fallback(HttpStatus status) {
        return "{\"status\":%d,\"title\":\"%s\"}".formatted(status.value(), status.getReasonPhrase())
                .getBytes(StandardCharsets.UTF_8);
    }
}
