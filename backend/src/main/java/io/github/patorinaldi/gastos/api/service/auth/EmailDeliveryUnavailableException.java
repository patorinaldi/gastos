package io.github.patorinaldi.gastos.api.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Lo pedido necesita mandar un enlace de un solo uso por correo, y el envío configurado no puede
 * llevarlo sin exponerlo, como el de consola fuera de desarrollo (RNF-09). Lo responden el alta, el
 * pedido de restablecimiento y el reenvío de la verificación.
 */
public class EmailDeliveryUnavailableException extends ErrorResponseException {

    public EmailDeliveryUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                        "Por ahora no podemos enviar correos. Probá de nuevo más tarde."),
                null);
    }
}
