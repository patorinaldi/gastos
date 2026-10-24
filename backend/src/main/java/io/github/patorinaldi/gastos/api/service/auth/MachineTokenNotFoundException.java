package io.github.patorinaldi.gastos.api.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * El token no existe o no es de quien lo pide. Es la misma respuesta para los dos casos: un 403
 * para los tokens ajenos confirmaría que el identificador existe.
 */
public class MachineTokenNotFoundException extends ErrorResponseException {

    public MachineTokenNotFoundException() {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No existe ese token."),
                null);
    }
}
