package io.github.patorinaldi.gastos.api.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * La contraseña que confirma una operación sensible no es la de la cuenta. Responde 403 y no 401: la
 * sesión sigue siendo válida, y un 401 haría que el cliente web la descarte.
 */
public class PasswordConfirmationFailedException extends ErrorResponseException {

    public PasswordConfirmationFailedException() {
        super(HttpStatus.FORBIDDEN,
                ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "La contraseña no es correcta"),
                null);
    }
}
