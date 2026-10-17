package io.github.patorinaldi.gastos.api.service.limit;

import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Se superó un límite de intentos fallidos. No dice cuántos intentos quedaban ni cuál de los
 * límites se superó (CU-02, 2a.2). El header {@code Retry-After} indica en segundos cuándo se
 * puede volver a intentar.
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
