package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.ContainersConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La dirección del cliente detrás del proxy de Azure, con un Tomcat real: MockMvc no pasa por la
 * válvula que lee {@code X-Forwarded-For}.
 *
 * <p>Reproduce lo que llega en el despliegue: el proxy agrega al final del header la dirección
 * real con su puerto ({@code 203.0.113.50:40001}), después de lo que haya mandado el cliente. Acá
 * el proxy de confianza es 127.0.0.1, porque la prueba se conecta desde ahí.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "gastos.email.sender=console",
        "gastos.jwt.secret=clave-de-pruebas-de-integracion-32+",
        "server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1"
})
@Import(ContainersConfig.class)
class ForwardedClientAddressTest {

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    // Diez fallos desde la misma dirección real, cada uno con otro puerto y otra dirección
    // inventada adelante: si el puerto o lo inventado contaran, ningún contador llegaría al tope.
    @Test
    void cuentaLaDireccionRealSinPuertoEIgnoraLoQueMandaElCliente() throws Exception {
        String email = "nadie-" + UUID.randomUUID() + "@ejemplo.test";
        for (int i = 0; i < 10; i++) {
            int status = login(email, "198.51.100." + i + ", 203.0.113.50:" + (40000 + i));
            assertThat(status).isEqualTo(401);
        }

        assertThat(login(email, "192.0.2.99, 203.0.113.50:50000")).isEqualTo(429);
        assertThat(login(email, "203.0.113.51:50000")).isEqualTo(401);
    }

    private int login(String email, String forwardedFor) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email": "%s", "password": "otra-contraseña"}""".formatted(email)))
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
