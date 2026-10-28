package io.github.patorinaldi.gastos.api.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** La persona ya tiene el máximo de tokens de cliente máquina vigentes (RNF-33). */
public class TooManyActiveMachineTokensException extends ErrorResponseException {

    public TooManyActiveMachineTokensException(int max) {
        super(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                        "Ya tenés " + max + " tokens vigentes. Revocá alguno que no uses para emitir otro."),
                null);
    }
}
