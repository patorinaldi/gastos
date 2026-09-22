package io.github.patorinaldi.gastos.api.config;

import io.github.patorinaldi.gastos.api.service.email.ConsoleEmailSender;
import io.github.patorinaldi.gastos.api.service.email.EmailSender;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elige la implementación de EmailSender según {@code gastos.email.sender}.
 *
 * <p>Si la propiedad falta, la aplicación no arranca. Las dos alternativas son peores: con un
 * valor por defecto de desarrollo, un despliegue distraído escribe los tokens en el log; sin
 * ningún EmailSender, arranca igual y no envía nada hasta que alguien intenta registrarse.
 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
class EmailConfiguration {

    @Bean
    EmailSender emailSender(EmailProperties properties) {
        if (properties.sender() == null) {
            throw new IllegalStateException(
                    "Falta gastos.email.sender (variable EMAIL_SENDER). En desarrollo, arrancar con el perfil dev");
        }

        return switch (properties.sender()) {
            case CONSOLE -> new ConsoleEmailSender();
        };
    }
}
