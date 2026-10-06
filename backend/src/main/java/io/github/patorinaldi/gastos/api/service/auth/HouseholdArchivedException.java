package io.github.patorinaldi.gastos.api.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Credenciales correctas de un usuario cuyo hogar está archivado (RN-18). Sin este rechazo, el
 * login emitiría un token que el resto de la API rechaza en cada petición, y el cliente quedaría
 * en un ciclo entre el login y el 401. Como se responde después de validar la contraseña, no
 * revela nada a quien no la conoce.
 *
 * <p>No debería ocurrir: un hogar se archiva cuando lo deja su último integrante, así que nadie
 * queda dentro de uno archivado. El chequeo es el mismo que hace la sesión en cada petición.
 */
public class HouseholdArchivedException extends ErrorResponseException {

    public HouseholdArchivedException() {
        super(HttpStatus.FORBIDDEN,
                ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                        "Tu cuenta no tiene un hogar activo, así que no puede iniciar sesión."),
                null);
    }
}