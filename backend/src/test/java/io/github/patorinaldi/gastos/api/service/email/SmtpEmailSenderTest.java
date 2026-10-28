package io.github.patorinaldi.gastos.api.service.email;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpEmailSenderTest {

    private static final String FROM = "Gastos <no-responder@gastos.ejemplo>";

    private final JavaMailSender mailSender = mock(JavaMailSender.class);

    @Test
    void mandaElCorreoConRemitenteDestinatarioAsuntoYCuerpo() {
        new SmtpEmailSender(mailSender, FROM)
                .send(new EmailMessage("ana@ejemplo.com", "Confirmá tu correo", "Abrí este enlace"));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertThat(sent.getValue().getFrom()).isEqualTo(FROM);
        assertThat(sent.getValue().getTo()).containsExactly("ana@ejemplo.com");
        assertThat(sent.getValue().getSubject()).isEqualTo("Confirmá tu correo");
        assertThat(sent.getValue().getText()).isEqualTo("Abrí este enlace");
    }

    // Es el envío real: puede llevar tokens, a diferencia del de consola.
    @Test
    void puedeLlevarTokens() {
        assertThat(new SmtpEmailSender(mailSender, FROM).canSendSecrets()).isTrue();
    }

    // El remitente es una cabecera: con un salto de línea se podría inyectar otra.
    @Test
    void rechazaUnRemitenteEnBlancoOConSaltosDeLinea() {
        assertThatThrownBy(() -> new SmtpEmailSender(mailSender, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SmtpEmailSender(mailSender, "a@b.com\r\nBcc: x@y.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
