package io.github.patorinaldi.gastos.api.service.category;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Ya hay una categoría activa con ese nombre en el hogar, sin distinguir mayúsculas (RN-09). Una dada
 * de baja no ocupa su nombre.
 */
public class CategoryNameTakenException extends ErrorResponseException {

    public CategoryNameTakenException() {
        super(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Ya existe una categoría con ese nombre."),
                null);
    }
}