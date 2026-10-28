package io.github.patorinaldi.gastos.api.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * El enlace de restablecimiento no sirve: no existe, venció o ya se usó (RN-03, CU-08 5a). Es la
 * misma respuesta para los tres casos, como la de los códigos de invitación.
 */
public class ResetTokenUnavailableException extends ErrorResponseException {

    public ResetTokenUnavailableException() {
        super(HttpStatus.GONE,
                ProblemDetail.forStatusAndDetail(HttpStatus.GONE,
                        "El enlace no es válido o ya venció. Pedí uno nuevo."),
                null);
    }
}
