package io.github.patorinaldi.gastos.api.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementación de desarrollo: no envía nada, escribe cada correo en el log para poder
 * seguir los enlaces de verificación sin un proveedor real.
 *
 * <p>No debe quedar activa en producción: el cuerpo de los correos lleva tokens de un solo
 * uso, y cualquiera con acceso al log podría verificar cuentas o cambiar contraseñas ajenas.
 */
@Component
@ConditionalOnProperty(name = "gastos.email.sender", havingValue = "console")
public class ConsoleEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

    public ConsoleEmailSender() {
        log.warn("Correo por consola: los correos no se envían, se escriben en el log. No usar en producción");
    }

    @Override
    public void send(EmailMessage message) {
        log.info("""
                Correo no enviado (gastos.email.sender=console)
                  Para:   {}
                  Asunto: {}
                {}""", message.to(), message.subject(), message.body());
    }
}
