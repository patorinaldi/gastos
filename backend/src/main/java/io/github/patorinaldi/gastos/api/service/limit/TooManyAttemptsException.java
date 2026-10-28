package io.github.patorinaldi.gastos.api.service.limit;

import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Se superó un límite de intentos: de inicio de sesión (RNF-06), de códigos de invitación
 * (RNF-30), de altas de cuenta (RNF-31) o de pedidos de restablecimiento de contraseña (RNF-32).
 * No dice cuántos intentos quedaban ni cuál de los límites se superó (CU-02, 2a.2; CU-03, 2c.2),
 * para no orientar a quien prueba. El header {@code Retry-After} indica en segundos cuándo se puede
 * volver a intentar.
 */
public class TooManyAttemptsException extends ErrorResponseException {

    public TooManyAttemptsException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS,
                ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                        "Demasiados intentos fallidos. Esperá unos minutos y volvé a intentar."),
                null);
        getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(roundUpToSeconds(retryAfter)));
    }

    // Redondea hacia arriba y nunca menos de 1: un 0 invitaría a reintentar antes de que la
    // ventana cierre.
    private static long roundUpToSeconds(Duration duration) {
        long seconds = duration.toSeconds() + (duration.toNanosPart() > 0 ? 1 : 0);
        return Math.max(1, seconds);
    }
}
