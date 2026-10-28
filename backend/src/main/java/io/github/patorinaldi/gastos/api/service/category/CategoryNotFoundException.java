package io.github.patorinaldi.gastos.api.service.category;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * La categoría no existe, está dada de baja o es de otro hogar. Es la misma respuesta para los tres:
 * un 403 para la de otro hogar confirmaría que el identificador existe. Una dada de baja ya no se
 * ofrece, así que tampoco se renombra ni se vuelve a dar de baja.
 */
public class CategoryNotFoundException extends ErrorResponseException {

    public CategoryNotFoundException() {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No existe esa categoría."),
                null);
    }
}