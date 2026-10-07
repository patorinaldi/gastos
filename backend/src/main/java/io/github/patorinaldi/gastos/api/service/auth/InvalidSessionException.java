package io.github.patorinaldi.gastos.api.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * La sesión dejó de corresponder a un usuario o a un hogar entre la autenticación de la petición y
 * su atención. Es un 401 y no un error interno: lo que corresponde es volver a iniciar sesión.
 */
public class InvalidSessionException extends ErrorResponseException {

    public InvalidSessionException() {
        super(HttpStatus.UNAUTHORIZED,
                ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                        "La sesión ya no es válida. Iniciá sesión de nuevo."),
                null);
    }
}