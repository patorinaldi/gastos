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
 * @param from                remitente de los correos enviados por SMTP
 */
@ConfigurationProperties("gastos.email")
record EmailProperties(Sender sender, boolean consoleAllowSecrets, String from) {

    enum Sender {

        /** Solo desarrollo: escribe los correos en el log en lugar de enviarlos. */
        CONSOLE,

        /** Envío real, por el servidor SMTP de {@code spring.mail.*}. */
        SMTP
    }
}
