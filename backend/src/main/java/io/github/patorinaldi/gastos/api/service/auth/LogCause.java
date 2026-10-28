package io.github.patorinaldi.gastos.api.service.auth;

import java.util.ArrayList;
import java.util.List;

/**
 * Cómo se registra en el log la falla de un envío de correo sin escribir el destinatario (RNF-09).
 *
 * <p>Las excepciones de JavaMail llevan la dirección en el mensaje ("Invalid Addresses: ..."), y el
 * stack trace la repite en cada causa. Del error quedan solo las clases de la cadena de causas, que
 * alcanzan para distinguir un rechazo del destinatario de una falla de autenticación o de conexión.
 */
final class LogCause {

    private static final int MAX_DEPTH = 5;

    private LogCause() {
    }

    /** Las clases de la cadena de causas, de afuera hacia adentro, por ejemplo {@code MailSendException <- SendFailedException}. */
    static String of(Throwable error) {
        List<String> names = new ArrayList<>();
        for (Throwable cause = error; cause != null && names.size() < MAX_DEPTH; cause = cause.getCause()) {
            names.add(cause.getClass().getSimpleName());
            if (cause.getCause() == cause) {
                break;
            }
        }
        return String.join(" <- ", names);
    }
}
