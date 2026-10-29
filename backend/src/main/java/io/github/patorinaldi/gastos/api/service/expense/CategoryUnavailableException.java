package io.github.patorinaldi.gastos.api.service.expense;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * La categoría que se quiere asignar a un gasto no existe, es de otro hogar o está dada de baja
 * (RN-08, RN-16). Las tres responden igual, por el mismo motivo que {@link ExpenseNotFoundException}.
 *
 * <p>Es un 400 y no un 404: lo que no existe es un dato del cuerpo, no el recurso de la ruta.
 */
public class CategoryUnavailableException extends ErrorResponseException {

    public CategoryUnavailableException() {
        super(HttpStatus.BAD_REQUEST,
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                        "La categoría no existe o está dada de baja."),
                null);
    }
}
