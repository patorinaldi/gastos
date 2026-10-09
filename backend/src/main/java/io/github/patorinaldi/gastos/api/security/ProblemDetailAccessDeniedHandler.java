package io.github.patorinaldi.gastos.api.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 403 de la cadena de seguridad como {@code application/problem+json}: hay una sesión válida, pero
 * no habilita lo que se pidió. Hoy pasa con {@code /api/capture}, que la sesión web no habilita.
 *
 * <p>Igual que {@link ProblemDetailAuthenticationEntryPoint}: primero el handler de Spring, que fija
 * el 403 y el header {@code WWW-Authenticate}, y después el cuerpo con el formato del resto de la
 * API.
 */
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    static final String DETAIL = "Tu sesión no tiene permiso para usar este recurso.";

    private final AccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
    private final HandlerExceptionResolver exceptionResolver;

    public ProblemDetailAccessDeniedHandler(HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        bearer.handle(request, response, accessDeniedException);
        exceptionResolver.resolveException(request, response, null, new ErrorResponseException(
                HttpStatus.FORBIDDEN,
                ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, DETAIL),
                accessDeniedException));
    }
}