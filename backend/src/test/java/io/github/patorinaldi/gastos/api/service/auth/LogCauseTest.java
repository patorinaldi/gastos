package io.github.patorinaldi.gastos.api.service.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import static org.assertj.core.api.Assertions.assertThat;

class LogCauseTest {

    // Las excepciones de JavaMail llevan la dirección en el mensaje: del error quedan solo las clases.
    @Test
    void nombraLasClasesDeLaCadenaSinLosMensajes() {
        Exception error = new MailSendException("Failed messages: Invalid Addresses: ana@ejemplo.test",
                new IllegalStateException("550 <ana@ejemplo.test> no existe"));

        assertThat(LogCause.of(error))
                .isEqualTo("MailSendException <- IllegalStateException")
                .doesNotContain("ana@ejemplo.test");
    }
}
