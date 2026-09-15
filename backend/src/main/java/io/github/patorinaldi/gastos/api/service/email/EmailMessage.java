package io.github.patorinaldi.gastos.api.service.email;

import java.util.Objects;

/**
 * Correo transaccional en texto plano.
 */
public record EmailMessage(String to, String subject, String body) {

    public EmailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(body, "body");
    }
}
