package io.github.patorinaldi.gastos.api.config;

import io.github.patorinaldi.gastos.api.service.email.ConsoleEmailSender;
import io.github.patorinaldi.gastos.api.service.email.EmailSender;
import io.github.patorinaldi.gastos.api.service.email.SmtpEmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Elige la implementación de EmailSender según {@code gastos.email.sender}.
 *
 * <p>Si la propiedad falta, la aplicación no arranca. Las dos alternativas son peores: con un
 * valor por defecto de desarrollo, un despliegue distraído escribe los tokens en el log; sin
 * ningún EmailSender, arranca igual y no envía nada hasta que alguien intenta registrarse. Con
 * {@code smtp}, tampoco arranca si falta el servidor o el remitente, por lo mismo.
 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
class EmailConfiguration {

    @Bean
    EmailSender emailSender(EmailProperties properties,
                            ObjectProvider<JavaMailSender> mailSender,
                            @Value("${spring.mail.host:}") String mailHost) {
        if (properties.sender() == null) {
            throw new IllegalStateException(
                    "Falta gastos.email.sender (variable EMAIL_SENDER). En desarrollo, arrancar con el perfil dev");
        }

        return switch (properties.sender()) {
            case CONSOLE -> new ConsoleEmailSender(properties.consoleAllowSecrets());
            case SMTP -> smtp(properties, mailSender, mailHost);
        };
    }

    private static EmailSender smtp(EmailProperties properties,
                                    ObjectProvider<JavaMailSender> mailSender,
                                    String mailHost) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (mailHost.isBlank() || sender == null) {
            throw new IllegalStateException(
                    "gastos.email.sender=smtp necesita el servidor SMTP: falta spring.mail.host (variable MAIL_HOST)");
        }
        if (properties.from() == null || properties.from().isBlank()) {
            throw new IllegalStateException(
                    "gastos.email.sender=smtp necesita el remitente: falta gastos.email.from (variable EMAIL_FROM)");
        }
        return new SmtpEmailSender(sender, properties.from());
    }
}
