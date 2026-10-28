package io.github.patorinaldi.gastos.api.service.category;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** La regla no existe o es de otro hogar, por el mismo motivo que {@link CategoryNotFoundException}. */
public class CategoryRuleNotFoundException extends ErrorResponseException {

    public CategoryRuleNotFoundException() {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No existe esa regla."),
                null);
    }
}