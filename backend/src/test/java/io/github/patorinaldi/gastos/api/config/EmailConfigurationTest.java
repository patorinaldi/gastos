package io.github.patorinaldi.gastos.api.config;

import io.github.patorinaldi.gastos.api.service.email.ConsoleEmailSender;
import io.github.patorinaldi.gastos.api.service.email.EmailSender;
import io.github.patorinaldi.gastos.api.service.email.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

class EmailConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EmailConfiguration.class);

    @Test
    void usesTheConsoleSenderWhenConfiguredAsConsole() {
        contextRunner
                .withPropertyValues("gastos.email.sender=console")
                .run(context -> assertThat(context).getBean(EmailSender.class).isInstanceOf(ConsoleEmailSender.class));
    }

    // Lo que pasa en un despliegue con EMAIL_SENDER=console: no puede mandar enlaces de un solo uso.
    @Test
    void theConsoleSenderRefusesSecretsUnlessAllowed() {
        contextRunner
                .withPropertyValues("gastos.email.sender=console")
                .run(context -> assertThat(context.getBean(EmailSender.class).canSendSecrets()).isFalse());
        contextRunner
                .withPropertyValues("gastos.email.sender=console", "gastos.email.console-allow-secrets=true")
                .run(context -> assertThat(context.getBean(EmailSender.class).canSendSecrets()).isTrue());
    }

    @Test
    void usesTheSmtpSenderWithAServerAndASender() {
        contextRunner
                .withBean(JavaMailSender.class, JavaMailSenderImpl::new)
                .withPropertyValues(
                        "gastos.email.sender=smtp",
                        "spring.mail.host=smtp.ejemplo",
                        "gastos.email.from=no-responder@gastos.ejemplo")
                .run(context -> assertThat(context).getBean(EmailSender.class).isInstanceOf(SmtpEmailSender.class));
    }

    // Arrancar sin servidor dejaría el alta aceptando cuentas que nunca reciben su enlace.
    @Test
    void theSmtpSenderDoesNotStartWithoutAServer() {
        contextRunner
                .withPropertyValues("gastos.email.sender=smtp", "gastos.email.from=no-responder@gastos.ejemplo")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("MAIL_HOST"));
    }

    @Test
    void theSmtpSenderDoesNotStartWithoutASender() {
        contextRunner
                .withBean(JavaMailSender.class, JavaMailSenderImpl::new)
                .withPropertyValues("gastos.email.sender=smtp", "spring.mail.host=smtp.ejemplo")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("EMAIL_FROM"));
    }

    // Con autenticación y sin credenciales, cada envío fallaría en segundo plano y el alta seguiría
    // respondiendo 201: ninguna cuenta se podría verificar.
    @Test
    void theSmtpSenderDoesNotStartWithAuthAndWithoutCredentials() {
        contextRunner
                .withBean(JavaMailSender.class, JavaMailSenderImpl::new)
                .withPropertyValues(
                        "gastos.email.sender=smtp",
                        "spring.mail.host=smtp.ejemplo",
                        "spring.mail.properties.mail.smtp.auth=true",
                        "spring.mail.username=gastos",
                        "gastos.email.from=no-responder@gastos.ejemplo")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("MAIL_PASSWORD"));
    }

    @Test
    void theSmtpSenderStartsWithAuthAndCredentials() {
        contextRunner
                .withBean(JavaMailSender.class, JavaMailSenderImpl::new)
                .withPropertyValues(
                        "gastos.email.sender=smtp",
                        "spring.mail.host=smtp.ejemplo",
                        "spring.mail.properties.mail.smtp.auth=true",
                        "spring.mail.username=gastos",
                        "spring.mail.password=secreto-de-prueba",
                        "gastos.email.from=no-responder@gastos.ejemplo")
                .run(context -> assertThat(context).getBean(EmailSender.class).isInstanceOf(SmtpEmailSender.class));
    }

    // Con el servidor definido, la falta del JavaMailSender no es culpa de MAIL_HOST.
    @Test
    void theSmtpSenderBlamesTheMissingMailSenderAndNotTheServer() {
        contextRunner
                .withPropertyValues(
                        "gastos.email.sender=smtp",
                        "spring.mail.host=smtp.ejemplo",
                        "gastos.email.from=no-responder@gastos.ejemplo")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("JavaMailSender")
                        .satisfies(failure -> assertThat(failure).rootCause().message().doesNotContain("MAIL_HOST")));
    }

    @Test
    void doesNotStartWithoutTheProperty() {
        contextRunner.run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasStackTraceContaining("gastos.email.sender"));
    }

    @Test
    void doesNotStartWithAnUnknownSender() {
        contextRunner
                .withPropertyValues("gastos.email.sender=carrier-pigeon")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("gastos.email.sender"));
    }
}
