package io.github.patorinaldi.gastos.api.service.category;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * El patrón, normalizado, no sirve como regla: tiene menos de {@link Patterns#MIN_KEY_LENGTH} letras
 * o números, y coincidiría con comercios que no tienen nada que ver, o tiene más de
 * {@link Patterns#MAX_LENGTH} caracteres y no entra en la columna.
 */
public class InvalidPatternException extends ErrorResponseException {

    private InvalidPatternException(String detail) {
        super(HttpStatus.BAD_REQUEST, ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail), null);
    }

    static InvalidPatternException tooShort() {
        return new InvalidPatternException(
                "El patrón necesita al menos " + Patterns.MIN_KEY_LENGTH + " letras o números.");
    }

    static InvalidPatternException tooLong() {
        return new InvalidPatternException(
                "El patrón no puede tener más de " + Patterns.MAX_LENGTH + " caracteres.");
    }
}
