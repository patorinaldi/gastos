package io.github.patorinaldi.gastos.api.service.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ConsoleEmailSenderTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ConsoleEmailSender.class);

    @Test
    void writesRecipientSubjectAndBodyToLog(CapturedOutput output) {
        new ConsoleEmailSender().send(new EmailMessage(
                "ana@example.com", "Verifica tu correo", "http://localhost:5173/verify?token=abc123"));

        assertThat(output).contains(
                "ana@example.com", "Verifica tu correo", "http://localhost:5173/verify?token=abc123");
    }

    @Test
    void isTheEmailSenderWhenConfiguredAsConsole() {
        contextRunner
                .withPropertyValues("gastos.email.sender=console")
                .run(context -> assertThat(context).getBean(EmailSender.class).isInstanceOf(ConsoleEmailSender.class));
    }

    @Test
    void isNotRegisteredWhenAnotherSenderIsConfigured() {
        contextRunner
                .withPropertyValues("gastos.email.sender=smtp")
                .run(context -> assertThat(context).doesNotHaveBean(EmailSender.class));
    }
}
