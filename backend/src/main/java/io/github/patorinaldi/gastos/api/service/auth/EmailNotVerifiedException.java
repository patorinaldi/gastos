package io.github.patorinaldi.gastos.api.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Credenciales correctas de una cuenta cuyo correo todavía no se verificó (RN-02, RF-04). Solo
 * se responde después de validar la contraseña: antes, permitiría averiguar el estado de
 * cualquier cuenta con solo conocer su correo.
 */
public class EmailNotVerifiedException extends ErrorResponseException {

    public EmailNotVerifiedException() {
        super(HttpStatus.FORBIDDEN,
                ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                        "El correo todavía no fue verificado. Revisá el enlace que te enviamos."),
                null);
    }
}