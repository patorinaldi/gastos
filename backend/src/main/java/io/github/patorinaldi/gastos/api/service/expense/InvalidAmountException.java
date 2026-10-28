package io.github.patorinaldi.gastos.api.service.expense;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * El importe no se pudo interpretar sin ambigüedad (RF-32, RN-06). El detalle dice por qué, para que
 * quien lo envió pueda corregirlo; no repite el valor recibido.
 */
public class InvalidAmountException extends ErrorResponseException {

    public InvalidAmountException(String reason) {
        super(HttpStatus.BAD_REQUEST,
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El importe " + reason + "."),
                null);
    }
}
