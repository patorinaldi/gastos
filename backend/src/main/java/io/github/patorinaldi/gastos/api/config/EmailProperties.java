package io.github.patorinaldi.gastos.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Implementación de {@code EmailSender} que usa la aplicación.
 *
 * <p>Un valor desconocido no liga y la aplicación no arranca, en lugar de quedarse sin envío
 * de correo.
 *
 * @param consoleAllowSecrets si el envío por consola acepta correos con tokens de un solo uso, que
 *                            quedan escritos en el log. Solo para desarrollo y pruebas; por defecto,
 *                            no
 */
@ConfigurationProperties("gastos.email")
record EmailProperties(Sender sender, boolean consoleAllowSecrets) {

    enum Sender {

        /** Solo desarrollo: escribe los correos en el log en lugar de enviarlos. */
        CONSOLE
    }
}
