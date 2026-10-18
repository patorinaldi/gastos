package io.github.patorinaldi.gastos.api.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Ya hay una cuenta con ese correo, sin distinguir mayúsculas (RN-01).
 *
 * <p>A diferencia del login y del canje de invitaciones, acá la respuesta sí dice qué pasó, porque
 * es el contrato documentado del alta y porque callarlo no protegería nada: quien quiera saber si
 * un correo tiene cuenta lo averigua igual intentando registrarlo, y si el alta respondiera que
 * salió bien, la persona quedaría esperando un correo que nunca llega.
 */
public class EmailAlreadyRegisteredException extends ErrorResponseException {

    public EmailAlreadyRegisteredException() {
        super(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                        "Ya existe una cuenta con ese correo. Probá iniciar sesión o recuperar la contraseña."),
                null);
    }
}
