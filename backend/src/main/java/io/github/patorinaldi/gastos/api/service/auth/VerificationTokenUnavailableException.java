package io.github.patorinaldi.gastos.api.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * El enlace de verificación no sirve: no existe, venció o ya se usó (RN-03, HU-02). Es la misma
 * respuesta para los tres casos, y ofrece pedir otro.
 */
public class VerificationTokenUnavailableException extends ErrorResponseException {

    public VerificationTokenUnavailableException() {
        super(HttpStatus.GONE,
                ProblemDetail.forStatusAndDetail(HttpStatus.GONE,
                        "El enlace no es válido o ya venció. Pedí que te enviemos uno nuevo."),
                null);
    }
}
