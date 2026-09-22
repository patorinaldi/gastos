package io.github.patorinaldi.gastos.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Implementación de {@code EmailSender} que usa la aplicación.
 *
 * <p>Un valor desconocido no liga y la aplicación no arranca, en lugar de quedarse sin envío
 * de correo.
 */
@ConfigurationProperties("gastos.email")
record EmailProperties(Sender sender) {

    enum Sender {

        /** Solo desarrollo: escribe los correos en el log en lugar de enviarlos. */
        CONSOLE
    }
}
