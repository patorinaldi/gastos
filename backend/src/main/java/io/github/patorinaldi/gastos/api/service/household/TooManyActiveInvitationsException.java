package io.github.patorinaldi.gastos.api.service.household;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * El hogar ya tiene el máximo de invitaciones vigentes (RNF-30). Cada código vigente es uno más
 * para acertar al tantear, así que no se generan más hasta que alguno se canjee o venza.
 */
public class TooManyActiveInvitationsException extends ErrorResponseException {

    public TooManyActiveInvitationsException(int max) {
        super(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                        "El hogar ya tiene " + max + " invitaciones vigentes. Podés generar otra cuando"
                                + " alguna se use o venza."),
                null);
    }
}
