package io.github.patorinaldi.gastos.api.service.expense;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * El gasto no existe, está dado de baja o es de otro hogar. Es la misma respuesta para los tres: un
 * 403 para el de otro hogar confirmaría que el identificador existe (HU-09).
 */
public class ExpenseNotFoundException extends ErrorResponseException {

    public ExpenseNotFoundException() {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No existe ese gasto."),
                null);
    }
}
