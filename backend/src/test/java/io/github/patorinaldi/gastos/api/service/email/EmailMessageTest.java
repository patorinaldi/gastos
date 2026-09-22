package io.github.patorinaldi.gastos.api.service.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailMessageTest {

    @Test
    void rejectsABlankRecipient() {
        assertThatThrownBy(() -> new EmailMessage("  ", "Asunto", "Cuerpo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLineBreaksInTheRecipient() {
        assertThatThrownBy(() -> new EmailMessage(
                "ana@example.com\r\nBcc: mallory@example.com", "Asunto", "Cuerpo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLineBreaksInTheSubject() {
        assertThatThrownBy(() -> new EmailMessage(
                "ana@example.com", "Asunto\nBcc: mallory@example.com", "Cuerpo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // El cuerpo no es una cabecera: ahí los saltos de línea son legítimos.
    @Test
    void acceptsAMultilineBody() {
        EmailMessage message = new EmailMessage("ana@example.com", "Asunto", "Primera linea\nSegunda linea");

        assertThat(message.body()).contains("\n");
    }
}
