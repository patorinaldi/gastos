package io.github.patorinaldi.gastos.api.service.email;

/**
 * Envío de correos transaccionales (verificación de cuenta, recuperación de contraseña).
 * La implementación activa se elige con la propiedad {@code gastos.email.sender}, de modo
 * que quien envía un correo no depende del proveedor.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
