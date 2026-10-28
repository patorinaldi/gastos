package io.github.patorinaldi.gastos.api.service.category;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * El patrón, normalizado, tiene menos de {@link Patterns#MIN_KEY_LENGTH} letras o números. Un patrón
 * así coincidiría con comercios que no tienen nada que ver.
 */
public class InvalidPatternException extends ErrorResponseException {

    public InvalidPatternException() {
        super(HttpStatus.BAD_REQUEST,
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El patrón necesita al menos 3 letras o números."),
                null);
    }
}