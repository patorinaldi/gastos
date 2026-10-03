package io.github.patorinaldi.gastos.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Orígenes desde los que el cliente web puede llamar a la API. Sin ninguno configurado, el
 * navegador rechaza toda llamada desde otro origen, que es el comportamiento seguro por defecto.
 */
@ConfigurationProperties("gastos.cors")
record CorsProperties(List<String> allowedOrigins) {

    CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}