package io.github.patorinaldi.gastos.api.service.household;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * El código no sirve para sumarse a un hogar: no existe, venció, ya se canjeó o su hogar está
 * archivado (RN-05, RN-18). Es la misma respuesta para los cuatro casos, para que no se puedan
 * descubrir códigos válidos por tanteo. El motivo real queda solo en el log del servidor.
 */
public class InvitationUnavailableException extends ErrorResponseException {

    public InvitationUnavailableException() {
        super(HttpStatus.GONE,
                ProblemDetail.forStatusAndDetail(HttpStatus.GONE,
                        "El código no es válido o ya venció. Pedile a quien te invitó uno nuevo."),
                null);
    }
}
