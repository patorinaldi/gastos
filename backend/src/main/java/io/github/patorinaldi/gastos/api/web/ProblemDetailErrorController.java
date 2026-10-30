package io.github.patorinaldi.gastos.api.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Responde los errores que no resolvió ningún handler con el mismo formato que el resto de la API:
 * {@code application/problem+json} (RF-40).
 *
 * <p>Los errores previstos ya salen así: los del dominio, que extienden {@code ErrorResponseException},
 * los de validación y los de Spring MVC los resuelven los Problem Details de Spring, y los 401 y 403
 * de la cadena de seguridad, {@code SecurityProblemDetailWriter}. Lo que queda es lo que se escapa:
 * una excepción no prevista en un controlador, como una violación de integridad que el servicio no
 * tradujo, o una que se lanza en un filtro. El contenedor la reenvía a {@code /error}, y este
 * controlador reemplaza al de Spring Boot, que respondía con otros campos ({@code timestamp},
 * {@code error}, {@code path}) o con una página HTML.
 *
 * <p>Nunca lee la excepción: su mensaje puede nombrar tablas, columnas o valores (RNF-17). Queda en
 * el log, que la escribe el contenedor antes de reenviar la petición.
 */
@RestController
class ProblemDetailErrorController implements ErrorController {

    static final String DETAIL = "Ocurrió un error inesperado. Intentá de nuevo en unos minutos.";

    /**
     * Un 4xx llega acá solo si alguien lo respondió con {@code sendError}, sin pasar por un handler:
     * no es inesperado, pero tampoco se sabe qué corregir.
     */
    static final String CLIENT_DETAIL = "No pudimos atender el pedido. Revisá los datos e intentá de nuevo.";

    // Sin el Content-Type fijo, Spring negociaría el formato con el Accept de la petición, y a un
    // navegador que pide HTML no tendría cómo responderle.
    @RequestMapping("${server.error.path:/error}")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus status = status(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                status.is4xxClientError() ? CLIENT_DETAIL : DETAIL);
        if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String path) {
            problem.setInstance(URI.create(path));
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    // Sin un estado de error en el reenvío, como cuando alguien pide /error directamente, se responde
    // un 500, igual que el controlador de Spring Boot.
    private static HttpStatus status(HttpServletRequest request) {
        if (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code) {
            HttpStatus status = HttpStatus.resolve(code);
            if (status != null && status.isError()) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
