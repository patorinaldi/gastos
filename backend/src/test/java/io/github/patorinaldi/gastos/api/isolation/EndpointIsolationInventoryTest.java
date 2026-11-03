package io.github.patorinaldi.gastos.api.isolation;

import io.github.patorinaldi.gastos.api.IntegrationTest;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toCollection;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Inventario de la batería de aislamiento: cada punto de acceso de la API figura acá, con la clase
 * que prueba que un hogar no alcanza los datos de otro por ese camino, o con el motivo por el que no
 * opera sobre un hogar.
 *
 * <p>La lista se compara con los puntos de acceso que Spring registra. Uno nuevo que no se sume acá
 * hace fallar la CI, y uno que se quita también, para que la lista no afirme una cobertura que ya
 * no corresponde. Lo que esta prueba no puede ver es el contenido de cada clase: que de verdad
 * cubra lectura, modificación y baja con ids del otro hogar lo asegura la doble revisión de las PR
 * que tocan aislamiento.
 */
class EndpointIsolationInventoryTest extends IntegrationTest {

    private static final String TESTS = "io.github.patorinaldi.gastos.api.";

    /** Punto de acceso → prueba que lo cubre, relativa a {@link #TESTS}. */
    private static final Map<String, String> CUBIERTOS = Map.ofEntries(
            entry("POST /api/auth/login", "isolation.IdentityAndHouseholdIsolationTest"),
            entry("GET /api/auth/me", "isolation.IdentityAndHouseholdIsolationTest"),
            entry("POST /api/auth/password/reset", "web.PasswordResetTest"),
            entry("POST /api/auth/machine-tokens", "web.MachineTokenManagementTest"),
            entry("GET /api/auth/machine-tokens", "web.MachineTokenManagementTest"),
            entry("DELETE /api/auth/machine-tokens/{id}", "web.MachineTokenManagementTest"),

            entry("GET /api/household", "isolation.IdentityAndHouseholdIsolationTest"),
            entry("PATCH /api/household", "isolation.IdentityAndHouseholdIsolationTest"),
            entry("GET /api/household/members", "isolation.IdentityAndHouseholdIsolationTest"),
            entry("POST /api/household/invitations", "isolation.IdentityAndHouseholdIsolationTest"),
            entry("POST /api/household/invitations/preview", "isolation.IdentityAndHouseholdIsolationTest"),
            entry("POST /api/household/invitations/redeem", "isolation.IdentityAndHouseholdIsolationTest"),

            entry("POST /api/expenses", "web.ExpenseTest"),
            entry("GET /api/expenses", "web.ExpenseTest"),
            entry("GET /api/expenses/{id}", "web.ExpenseTest"),
            entry("PATCH /api/expenses/{id}", "web.ExpenseTest"),
            entry("DELETE /api/expenses/{id}", "web.ExpenseTest"),

            entry("GET /api/categories", "web.CategoryTest"),
            entry("POST /api/categories", "web.CategoryTest"),
            entry("PATCH /api/categories/{id}", "web.CategoryTest"),
            entry("DELETE /api/categories/{id}", "web.CategoryTest"),

            entry("GET /api/category-rules", "web.CategoryRuleTest"),
            entry("POST /api/category-rules", "web.CategoryRuleTest"),
            entry("DELETE /api/category-rules/{id}", "web.CategoryRuleTest"),

            entry("GET /api/expenses/summary", "web.AnalysisTest"),
            entry("GET /api/expenses/monthly", "web.AnalysisTest"),
            entry("GET /api/expenses/merchants", "web.AnalysisTest"),

            entry("POST /api/capture", "web.CaptureTest"),
            entry("GET /api/capture/connection", "web.CaptureTest"));

    /** Punto de acceso → por qué no opera sobre un hogar existente. */
    private static final Map<String, String> SIN_HOGAR = Map.of(
            "POST /api/auth/register",
            "crea la cuenta con un hogar nuevo: no recibe ni alcanza uno existente",
            "POST /api/auth/verify",
            "canjea un token de un solo uso que solo marca verificada a la cuenta que lo recibió",
            "POST /api/auth/verify/resend",
            "recibe un correo y responde igual exista o no la cuenta (EmailVerificationTest)",
            "POST /api/auth/password/forgot",
            "recibe un correo y responde igual exista o no la cuenta (PasswordResetTest)");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void cadaPuntoDeAccesoDeLaApiFiguraEnElInventario() {
        Set<String> registrados = mappings.getHandlerMethods().keySet().stream()
                .flatMap(EndpointIsolationInventoryTest::endpoints)
                .filter(endpoint -> endpoint.contains(" /api/"))
                .collect(toCollection(TreeSet::new));
        Set<String> inventario = new TreeSet<>(CUBIERTOS.keySet());
        inventario.addAll(SIN_HOGAR.keySet());

        assertThat(minus(registrados, inventario))
                .as("puntos de acceso sin prueba de aislamiento en el inventario")
                .isEmpty();
        assertThat(minus(inventario, registrados))
                .as("puntos de acceso del inventario que ya no existen")
                .isEmpty();
    }

    @Test
    void ningunoFiguraComoCubiertoYComoAjenoAlHogar() {
        assertThat(CUBIERTOS.keySet()).doesNotContainAnyElementsOf(SIN_HOGAR.keySet());
    }

    // Si una prueba se renombra o se mueve, el inventario no puede seguir nombrándola.
    @Test
    void cadaPruebaNombradaExiste() {
        for (String clase : new TreeSet<>(CUBIERTOS.values())) {
            assertThatCode(() -> Class.forName(TESTS + clase)).as(clase).doesNotThrowAnyException();
        }
    }

    private static Set<String> minus(Set<String> from, Set<String> other) {
        Set<String> result = new TreeSet<>(from);
        result.removeAll(other);
        return result;
    }

    // Un @RequestMapping sin método atiende a todos: figura como ANY, para que no pase desapercibido.
    private static Stream<String> endpoints(RequestMappingInfo info) {
        Set<String> paths = info.getPatternValues();
        Set<String> methods = info.getMethodsCondition().getMethods().stream()
                .map(String::valueOf)
                .collect(toCollection(TreeSet::new));
        if (methods.isEmpty()) {
            methods.add("ANY");
        }
        return methods.stream().flatMap(method -> paths.stream().map(path -> method + " " + path));
    }
}
