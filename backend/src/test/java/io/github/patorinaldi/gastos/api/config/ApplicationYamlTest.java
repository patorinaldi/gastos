package io.github.patorinaldi.gastos.api.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las variables de entorno que documenta el README llegan a la configuración a través del
 * {@code application.yaml} real, sin el perfil dev.
 *
 * <p>Las demás pruebas fijan las propiedades directamente ({@code gastos.email.sender=console}), así
 * que no pueden detectar que falte un mapeo en el YAML. Faltaba el de {@code EMAIL_SENDER}, y la
 * aplicación desplegada no arrancaba: la variable estaba definida en el servidor, pero nadie la
 * leía. Acá las variables se pasan con su nombre de entorno, que es como las ve Spring en
 * producción.
 */
class ApplicationYamlTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void lasVariablesDeEntornoLleganALaConfiguracion() {
        contextRunner
                .withPropertyValues(
                        "EMAIL_SENDER=console",
                        "JWT_SECRET=una-clave-de-treinta-y-dos-caracteres",
                        "CORS_ALLOWED_ORIGINS=https://gastos.ejemplo,https://otro.ejemplo")
                .run(context -> {
                    assertThat(context.getBean(EmailProperties.class).sender())
                            .isEqualTo(EmailProperties.Sender.CONSOLE);
                    assertThat(context.getBean(JwtProperties.class).secret())
                            .isEqualTo("una-clave-de-treinta-y-dos-caracteres");
                    assertThat(context.getBean(CorsProperties.class).allowedOrigins())
                            .isEqualTo(List.of("https://gastos.ejemplo", "https://otro.ejemplo"));
                });
    }

    // Sin EMAIL_SENDER la aplicación no arranca: el YAML la deja vacía, sin valor por defecto, y
    // EmailConfiguration se niega a elegir una implementación.
    @Test
    void sinEmailSenderLaAplicacionNoArranca() {
        contextRunner
                .withUserConfiguration(EmailConfiguration.class)
                .withPropertyValues("JWT_SECRET=una-clave-de-treinta-y-dos-caracteres")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("EMAIL_SENDER"));
    }

    @EnableConfigurationProperties({EmailProperties.class, JwtProperties.class, CorsProperties.class})
    static class PropertiesConfiguration {
    }
}