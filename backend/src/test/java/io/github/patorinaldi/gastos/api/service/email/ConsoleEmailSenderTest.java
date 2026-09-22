package io.github.patorinaldi.gastos.api.service.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ConsoleEmailSenderTest {

    private final ConsoleEmailSender sender = new ConsoleEmailSender();

    @Test
    void writesRecipientSubjectAndBodyToLog(CapturedOutput output) {
        sender.send(new EmailMessage(
                "ana@example.com", "Verifica tu correo", "http://localhost:5173/verify?token=abc123"));

        assertThat(output).contains(
                "ana@example.com", "Verifica tu correo", "http://localhost:5173/verify?token=abc123");
    }

    @Test
    void prefixesEveryBodyLineSoNoneCanPassAsALogEntry(CapturedOutput output) {
        sender.send(new EmailMessage("ana@example.com", "Hola", "Primera linea\nINFO Cuenta verificada"));

        assertThat(output).contains("  | Primera linea", "  | INFO Cuenta verificada");
    }
}
