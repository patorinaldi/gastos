package io.github.patorinaldi.gastos.api.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 403 de la cadena de seguridad como {@code application/problem+json}: hay una sesión válida, pero
 * no habilita lo que se pidió. Hoy pasa con {@code /api/capture}, que la sesión web no habilita.
 *
 * <p>Igual que {@link ProblemDetailAuthenticationEntryPoint}: primero el handler de Spring, que fija
 * el header {@code WWW-Authenticate}, y después {@link SecurityProblemDetailWriter} escribe el
 * cuerpo.
 */
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    static final String DETAIL = "Tu sesión no tiene permiso para usar este recurso.";

    private final AccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
    private final SecurityProblemDetailWriter writer;

    public ProblemDetailAccessDeniedHandler(SecurityProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        bearer.handle(request, response, accessDeniedException);
        writer.write(request, response, HttpStatus.FORBIDDEN, DETAIL);
    }
}
