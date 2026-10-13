package io.github.patorinaldi.gastos.api.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 401 de la cadena de seguridad como {@code application/problem+json}, igual que los demás errores
 * de la API (RF-40). Lo usa toda petición sin sesión válida: sin token, con un token vencido o
 * alterado, o con uno que la sesión rechaza.
 *
 * <p>Primero delega en el {@link BearerTokenAuthenticationEntryPoint} de Spring, que fija el header
 * {@code WWW-Authenticate}: es lo que espera un cliente Bearer. Después
 * {@link SecurityProblemDetailWriter} escribe el cuerpo.
 *
 * <p>El mensaje es el mismo en todos los casos: distinguir "no mandaste token" de "tu sesión ya no
 * vale" no le sirve al usuario, y a quien prueba tokens le contaría de más.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String DETAIL =
            "Iniciá sesión para continuar. Si ya la habías iniciado, venció o dejó de ser válida.";

    private final AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final SecurityProblemDetailWriter writer;

    public ProblemDetailAuthenticationEntryPoint(SecurityProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        bearer.commence(request, response, authException);
        writer.write(request, response, HttpStatus.UNAUTHORIZED, DETAIL);
    }
}
