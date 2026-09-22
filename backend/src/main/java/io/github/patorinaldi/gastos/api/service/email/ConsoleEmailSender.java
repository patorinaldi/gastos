package io.github.patorinaldi.gastos.api.service.email;

import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementación de desarrollo: no envía nada, escribe cada correo en el log para poder
 * seguir los enlaces de verificación sin un proveedor real.
 *
 * <p>No debe quedar activa en producción: el cuerpo de los correos lleva tokens de un solo
 * uso, y cualquiera con acceso al log podría verificar cuentas o cambiar contraseñas ajenas.
 * Por eso {@code gastos.email.sender} no tiene valor por defecto: hay que pedirla explícitamente.
 */
public class ConsoleEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

    private static final String BODY_PREFIX = "  | ";

    public ConsoleEmailSender() {
        log.warn("Correo por consola: los correos no se envían, se escriben en el log. No usar en producción");
    }

    @Override
    public void send(EmailMessage message) {
        log.info("""
                Correo no enviado (gastos.email.sender=console)
                  Para:   {}
                  Asunto: {}
                {}""", message.to(), message.subject(), quoteBody(message.body()));
    }

    // El cuerpo se arma con datos que carga el usuario (su nombre, un comercio). Sin prefijo,
    // un valor con saltos de línea podría hacerse pasar por otra entrada del log.
    private static String quoteBody(String body) {
        return body.lines()
                .map(line -> BODY_PREFIX + line)
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
