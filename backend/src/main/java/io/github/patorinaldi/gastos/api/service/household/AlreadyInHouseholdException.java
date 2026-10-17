package io.github.patorinaldi.gastos.api.service.household;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * El código es del hogar que el usuario ya integra (CU-03, 3a). Se distingue del {@code 410}
 * porque solo revela el hogar propio. Sin este rechazo, la única integrante de un hogar que
 * canjeara su propio código archivaría el hogar en el que sigue estando.
 */
public class AlreadyInHouseholdException extends ErrorResponseException {

    public AlreadyInHouseholdException() {
        super(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                        "Ya integrás el hogar de esta invitación."),
                null);
    }
}
