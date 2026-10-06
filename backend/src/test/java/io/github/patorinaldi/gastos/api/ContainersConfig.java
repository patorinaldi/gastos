package io.github.patorinaldi.gastos.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    // Debe coincidir con la imagen del compose.yaml de desarrollo.
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17-alpine");

    // Lo crea db/init-admin-gestionado.sql al levantar el contenedor.
    private static final String ADMIN_USERNAME = "gastos_admin";
    private static final String ADMIN_PASSWORD = "gastos_admin";

    private static final String API_USERNAME = "gastos_api";
    private static final String API_PASSWORD = "gastos_api";

    @Bean
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE).withInitScript("db/init-admin-gestionado.sql");
    }

    // Sin @ServiceConnection: haría que Flyway y la app usen el superusuario del contenedor
    // y las pruebas nunca pasarían por RLS. Flyway migra como gastos_admin, que tiene los mismos
    // permisos que el administrador de una Postgres gestionada (no es superusuario), y la app se
    // conecta como gastos_api, el rol que crea la V2.
    @Bean
    DynamicPropertyRegistrar postgresProperties(PostgreSQLContainer<?> postgres) {
        return registry -> {
            registry.add("spring.flyway.url", postgres::getJdbcUrl);
            registry.add("spring.flyway.user", () -> ADMIN_USERNAME);
            registry.add("spring.flyway.password", () -> ADMIN_PASSWORD);
            registry.add("spring.flyway.placeholders.api_password", () -> API_PASSWORD);
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> API_USERNAME);
            registry.add("spring.datasource.password", () -> API_PASSWORD);
        };
    }
}