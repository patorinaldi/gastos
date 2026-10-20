package io.github.patorinaldi.gastos.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 401 de los puntos de entrada de los clientes máquina, como {@code application/problem+json}.
 * Responde tanto a la petición sin token como a la que trae uno que no vale.
 *
 * <p>El header {@code WWW-Authenticate} anuncia el esquema {@code Token}, que es el que se espera
 * acá, y no {@code Bearer}. El mensaje es el mismo en todos los casos: no dice si el token existió,
 * si se revocó o si su dueño cambió de hogar (CU-07).
 */
public class MachineTokenAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String DETAIL =
            "El token de la automatización no es válido o ya no está vigente. Emití uno nuevo desde la aplicación.";

    private final SecurityProblemDetailWriter writer;

    public MachineTokenAuthenticationEntryPoint(SecurityProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, MachineTokenAuthenticationFilter.SCHEME);
        writer.write(request, response, HttpStatus.UNAUTHORIZED, DETAIL);
    }
}