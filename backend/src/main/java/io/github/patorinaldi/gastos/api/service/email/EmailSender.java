package io.github.patorinaldi.gastos.api.service.email;

/**
 * Envío de correos transaccionales (verificación de cuenta, recuperación de contraseña).
 * La implementación activa se elige con la propiedad {@code gastos.email.sender}, de modo
 * que quien envía un correo no depende del proveedor.
 */
public interface EmailSender {

    void send(EmailMessage message);

    /**
     * Si se le puede confiar un correo con un token de un solo uso: que el token quede solo en la
     * casilla de quien lo recibe. Quien emite tokens por correo tiene que consultarlo antes.
     */
    default boolean canSendSecrets() {
        return true;
    }
}
