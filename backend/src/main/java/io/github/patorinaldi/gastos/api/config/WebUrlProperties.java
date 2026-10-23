package io.github.patorinaldi.gastos.api.config;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Dirección del cliente web, para armar los enlaces de los correos (variable {@code WEB_URL}).
 *
 * <p>No tiene valor por defecto, por lo mismo que {@code EMAIL_SENDER}: con uno de desarrollo, un
 * despliegue que se olvide de definirla mandaría enlaces a {@code localhost} y nadie podría
 * restablecer su contraseña. Sin ella, la aplicación no arranca.
 *
 * @param url dirección base, por ejemplo {@code https://gastos.ejemplo}; una barra final se ignora
 */
@Validated
@ConfigurationProperties("gastos.web")
public record WebUrlProperties(@NotNull URI url) {

    /** {@code path} sobre la dirección base, sin barras repetidas. */
    public String link(String path) {
        String base = url.toString().replaceAll("/+$", "");
        return base + (path.startsWith("/") ? path : "/" + path);
    }
}
