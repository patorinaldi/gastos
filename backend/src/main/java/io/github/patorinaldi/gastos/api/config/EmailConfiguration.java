package io.github.patorinaldi.gastos.api.config;

import io.github.patorinaldi.gastos.api.service.email.ConsoleEmailSender;
import io.github.patorinaldi.gastos.api.service.email.EmailSender;
import io.github.patorinaldi.gastos.api.service.email.SmtpEmailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Elige la implementación de EmailSender según {@code gastos.email.sender}.
 *
 * <p>Si la propiedad falta, la aplicación no arranca. Las dos alternativas son peores: con un
 * valor por defecto de desarrollo, un despliegue distraído escribe los tokens en el log; sin
 * ningún EmailSender, arranca igual y no envía nada hasta que alguien intenta registrarse. Con
 * {@code smtp}, tampoco arranca si falta el servidor, el remitente o, con autenticación, las
 * credenciales, por lo mismo: el alta respondería 201 y ningún correo saldría.
 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
class EmailConfiguration {

    @Bean
    EmailSender emailSender(EmailProperties properties,
                            ObjectProvider<JavaMailSender> mailSender,
                            Environment environment) {
        if (properties.sender() == null) {
            throw new IllegalStateException(
                    "Falta gastos.email.sender (variable EMAIL_SENDER). En desarrollo, arrancar con el perfil dev");
        }

        return switch (properties.sender()) {
            case CONSOLE -> new ConsoleEmailSender(properties.consoleAllowSecrets());
            case SMTP -> smtp(properties, mailSender, environment);
        };
    }

    private static EmailSender smtp(EmailProperties properties,
                                    ObjectProvider<JavaMailSender> mailSender,
                                    Environment environment) {
        if (isBlank(environment.getProperty("spring.mail.host"))) {
            throw new IllegalStateException(
                    "gastos.email.sender=smtp necesita el servidor SMTP: falta spring.mail.host (variable MAIL_HOST)");
        }
        if (isBlank(properties.from())) {
            throw new IllegalStateException(
                    "gastos.email.sender=smtp necesita el remitente: falta gastos.email.from (variable EMAIL_FROM)");
        }
        boolean auth = environment.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class, false);
        if (auth && (isBlank(environment.getProperty("spring.mail.username"))
                || isBlank(environment.getProperty("spring.mail.password")))) {
            throw new IllegalStateException(
                    "gastos.email.sender=smtp con autenticación necesita las credenciales: faltan spring.mail.username"
                            + " o spring.mail.password (variables MAIL_USERNAME y MAIL_PASSWORD)");
        }
        // Con el servidor definido, Spring Boot crea el JavaMailSender. Si falta igual, es la
        // configuración de la aplicación, no la del despliegue.
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            throw new IllegalStateException(
                    "gastos.email.sender=smtp necesita un JavaMailSender, y no hay ninguno: revisar que la"
                            + " autoconfiguración de correo de Spring Boot no esté excluida");
        }
        return new SmtpEmailSender(sender, properties.from());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
