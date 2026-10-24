package io.github.patorinaldi.gastos.api.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * El restablecimiento de contraseña no está disponible: el envío de correos configurado no puede
 * llevar el enlace sin exponerlo, como el de consola fuera de desarrollo (RNF-09).
 */
public class PasswordResetUnavailableException extends ErrorResponseException {

    public PasswordResetUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                        "El restablecimiento de contraseña todavía no está disponible."),
                null);
    }
}
