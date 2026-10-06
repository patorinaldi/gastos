package io.github.patorinaldi.gastos.api.config;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La clave de firma no tiene valor por defecto fuera del perfil dev (RNF-07): sin ella, o con
 * una que se pueda adivinar por fuerza bruta, la aplicación no arranca.
 */
class JwtPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues("gastos.jwt.ttl=7d");

    @Test
    void arrancaConUnaClaveDeAlMenos32Caracteres() {
        contextRunner
                .withPropertyValues("gastos.jwt.secret=una-clave-de-treinta-y-dos-caracteres")
                .run(context -> assertThat(context.getBean(JwtProperties.class).ttl())
                        .isEqualTo(Duration.ofDays(7)));
    }

    @Test
    void noArrancaSinClave() {
        contextRunner.run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasStackTraceContaining("JWT_SECRET"));
    }

    // Es lo que pasa en un despliegue sin JWT_SECRET: application.yaml la deja vacía.
    @Test
    void noArrancaConLaClaveVacia() {
        contextRunner
                .withPropertyValues("gastos.jwt.secret=")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("JWT_SECRET"));
    }

    @Test
    void noArrancaConUnaClaveCorta() {
        contextRunner
                .withPropertyValues("gastos.jwt.secret=corta")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("al menos 32 caracteres"));
    }

    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfiguration {
    }
}