package io.github.patorinaldi.gastos.api.service.email;

import java.util.Objects;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Envío real, por SMTP. Sirve con cualquier proveedor que lo ofrezca (Brevo, SendGrid, Resend,
 * Gmail), así que cambiar de proveedor es cambiar variables de entorno, no código.
 *
 * <p>Los tiempos de espera de la conexión se fijan en {@code application.yaml}: sin ellos, un
 * servidor SMTP que no responde dejaría el hilo de envío esperando para siempre.
 */
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    /**
     * @param from remitente, por ejemplo {@code Gastos <no-responder@gastos.ejemplo>}; tiene que ser
     *             una dirección que el proveedor permita usar
     */
    public SmtpEmailSender(JavaMailSender mailSender, String from) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender");
        // Es una cabecera, como el destinatario: en blanco o con saltos de línea no va (ver EmailMessage).
        if (from == null || from.isBlank() || from.indexOf('\r') >= 0 || from.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("El remitente no puede estar en blanco ni tener saltos de línea");
        }
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        mailSender.send(mail);
    }
}
