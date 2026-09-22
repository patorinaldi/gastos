package io.github.patorinaldi.gastos.api.service.email;

import java.util.Objects;

/**
 * Correo transaccional en texto plano.
 *
 * <p>Destinatario y asunto son cabeceras del correo: se rechazan en blanco o con saltos de
 * línea, porque con un proveedor real una cabecera con CR/LF permite inyectar otras (un Bcc,
 * por ejemplo) en el mensaje enviado.
 */
public record EmailMessage(String to, String subject, String body) {

    public EmailMessage {
        to = requireHeader(to, "to");
        subject = requireHeader(subject, "subject");
        Objects.requireNonNull(body, "body");
        if (body.isBlank()) {
            throw new IllegalArgumentException("body no puede estar en blanco");
        }
    }

    private static String requireHeader(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " no puede estar en blanco");
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(field + " no puede contener saltos de línea");
        }
        return value;
    }
}
