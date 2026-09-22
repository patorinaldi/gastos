package io.github.patorinaldi.gastos.api.config;

import io.github.patorinaldi.gastos.api.service.email.ConsoleEmailSender;
import io.github.patorinaldi.gastos.api.service.email.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
                .withPropertyValues("gastos.email.sender=smtp")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("gastos.email.sender"));
    }
}
