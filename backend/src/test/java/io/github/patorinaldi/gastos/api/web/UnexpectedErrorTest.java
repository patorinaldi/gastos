package io.github.patorinaldi.gastos.api.web;

import com.jayway.jsonpath.JsonPath;
import io.github.patorinaldi.gastos.api.AuthTestSupport;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

/**
 * Un error que ningún handler previó responde como el resto de los errores de la API:
 * {@code application/problem+json}, con un mensaje para el usuario y sin detalle técnico (RF-40,
 * RNF-17).
 *
 * <p>Corre contra un servidor real y no contra MockMvc: una excepción que se escapa de un
 * controlador la atrapa el contenedor, que reenvía la petición a {@code /error}, y MockMvc no hace
 * ese reenvío. Por eso repite las propiedades de {@code IntegrationTest} con otro entorno web.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {
        "gastos.email.sender=console",
        "gastos.email.console-allow-secrets=true",
        "gastos.jwt.secret=clave-de-pruebas-de-integracion-32+",
        "gastos.web.url=http://localhost:5173"
})
class UnexpectedErrorTest extends AuthTestSupport {

    /** Lo que trae la excepción, que nunca tiene que llegar al cliente. */
    private static final String INTERNAL = "detalle interno: relación users, columna password_hash";

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    private UUID casa;
    private UUID ana;

    @BeforeEach
    void sembrar() {
        casa = createHousehold("Casa de Ana");
        ana = createUser(casa, uniqueEmail("ana"), "Ana");
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
    }

    @Test
    void unaExcepcionNoPrevistaRespondeProblemDetail500SinDetalleTecnico() throws Exception {
        HttpResponse<String> response = get(FailingProbe.UNEXPECTED, MediaType.APPLICATION_JSON_VALUE);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(contentType(response)).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(500);
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo(ProblemDetailErrorController.DETAIL);
        assertThat(JsonPath.<String>read(response.body(), "$.instance")).isEqualTo(FailingProbe.UNEXPECTED);
        assertThat(response.body()).doesNotContain(INTERNAL).doesNotContain("IllegalStateException");
    }

    // Una violación de integridad que el servicio no tradujo a un error del dominio: es el caso que
    // más fácil filtra detalle, porque el mensaje de Postgres nombra la tabla y la restricción.
    @Test
    void unaViolacionDeIntegridadNoPrevistaTampocoMuestraElMensajeDeLaBase() throws Exception {
        HttpResponse<String> response = get(FailingProbe.INTEGRITY, MediaType.APPLICATION_JSON_VALUE);

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(contentType(response)).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo(ProblemDetailErrorController.DETAIL);
        assertThat(response.body()).doesNotContain(INTERNAL).doesNotContain("DataIntegrityViolation");
    }

    // Como en la cadena de seguridad, el formato no se negocia con el Accept: un navegador que pide
    // HTML recibe igual el problem+json, y no la página de error de Spring Boot.
    @Test
    void elFormatoNoDependeDelAcceptDeLaPeticion() throws Exception {
        for (String accept : new String[] {MediaType.TEXT_HTML_VALUE, MediaType.TEXT_PLAIN_VALUE}) {
            HttpResponse<String> response = get(FailingProbe.UNEXPECTED, accept);

            assertThat(response.statusCode()).as(accept).isEqualTo(500);
            assertThat(contentType(response)).as(accept).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo(ProblemDetailErrorController.DETAIL);
        }
    }

    // Un 4xx respondido con sendError conserva su estado, con un mensaje que no habla de un error
    // inesperado.
    @Test
    void unErrorDelClienteSinHandlerConservaSuEstado() throws Exception {
        HttpResponse<String> response = get(FailingProbe.SEND_ERROR, MediaType.APPLICATION_JSON_VALUE);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(contentType(response)).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(JsonPath.<Integer>read(response.body(), "$.status")).isEqualTo(400);
        assertThat(JsonPath.<String>read(response.body(), "$.detail")).isEqualTo(ProblemDetailErrorController.CLIENT_DETAIL);
        assertThat(response.body()).doesNotContain(INTERNAL);
    }

    // Los mismos campos que un error que sí resuelve un controlador, ni uno más ni uno menos.
    @Test
    void tieneLosMismosCamposQueUnErrorDeControlador() throws Exception {
        HttpResponse<String> deControlador = get("/api/expenses/" + UUID.randomUUID(), MediaType.APPLICATION_JSON_VALUE);
        HttpResponse<String> noPrevisto = get(FailingProbe.UNEXPECTED, MediaType.APPLICATION_JSON_VALUE);

        assertThat(deControlador.statusCode()).isEqualTo(404);
        Map<String, Object> controlador = JsonPath.read(deControlador.body(), "$");
        Map<String, Object> error = JsonPath.read(noPrevisto.body(), "$");
        assertThat(error.keySet()).isEqualTo(controlador.keySet());
    }

    private HttpResponse<String> get(String path, String accept) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(AUTHORIZATION, bearer(ana))
                .header(ACCEPT, accept)
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String contentType(HttpResponse<String> response) {
        return response.headers().firstValue(CONTENT_TYPE).orElse("");
    }

    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        FailingProbe failingProbe() {
            return new FailingProbe();
        }
    }

    @RestController
    static class FailingProbe {

        static final String UNEXPECTED = "/api/probe/unexpected";
        static final String INTEGRITY = "/api/probe/integrity";
        static final String SEND_ERROR = "/api/probe/send-error";

        @GetMapping(UNEXPECTED)
        public Map<String, Object> unexpected() {
            throw new IllegalStateException(INTERNAL);
        }

        @GetMapping(INTEGRITY)
        public Map<String, Object> integrity() {
            throw new DataIntegrityViolationException(INTERNAL);
        }

        @GetMapping(SEND_ERROR)
        public void sendError(HttpServletResponse response) throws IOException {
            response.sendError(400, INTERNAL);
        }
    }
}
