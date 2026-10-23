package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.AuthTestSupport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El alta sobre el servicio, sin pasar por HTTP: lo que ocurre dentro de la transacción (RN-11) y
 * el camino que decide el índice único (RN-01).
 *
 * <p>Llaman a {@code create}, que no tiene la comprobación previa del correo. Por HTTP esa
 * comprobación frena el segundo intento antes de que exista nada que revertir, y la prueba pasaría
 * igual aunque la transacción no fuera atómica.
 *
 * <p>Lo que crean estas pruebas lleva una marca única en el nombre del hogar, y la limpieza borra
 * por ella: sigue funcionando aunque una prueba falle a mitad de camino, sin depender de que haya
 * llegado a anotar lo que creó.
 */
class RegistrationServiceTest extends AuthTestSupport {

    // Contenido que la columna acepta; estas pruebas no inician sesión, así que no hace falta bcrypt.
    private static final String HASH = "hash-irrelevante";

    @Autowired
    private RegistrationService service;

    private final String marca = UUID.randomUUID().toString();
    private final ExecutorService hilos = Executors.newFixedThreadPool(2);

    @AfterEach
    void limpiar() {
        hilos.shutdownNow();
        jdbcTemplate.queryForList(
                        "select id from households where name like ?", UUID.class, "Casa de " + marca + "%")
                .forEach(this::deleteHousehold);
    }

    @Test
    void unAltaConCorreoRepetidoNoDejaHogarHuerfano() {
        String email = uniqueEmail("ana");
        service.create(nombre("Ana"), email, HASH);

        assertThatThrownBy(() -> service.create(nombre("Otra"), email, HASH))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        // El hogar de la segunda alta se creó antes de fallar el usuario. Si la transacción no
        // revirtiera, quedaría acá, sin ningún integrante.
        assertThat(hogaresDe("Otra")).isZero();
        assertThat(hogaresDe("Ana")).isEqualTo(1);
    }

    // Solo la violación de users_email_key es "correo repetido". Un nombre de más de 200
    // caracteres viola otra restricción, y decir "ya existe una cuenta" escondería la causa real.
    @Test
    void otraViolacionDeIntegridadNoSeDisfrazaDeCorreoRepetido() {
        String nombreLargo = marca + "x".repeat(200);

        assertThatThrownBy(() -> service.create(nombreLargo, uniqueEmail("ana"), HASH))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from households where name like ?", Integer.class, "Casa de " + marca + "%"))
                .isZero();
    }

    /**
     * Dos altas con el mismo correo a la vez. Como no pasan por la comprobación previa, las dos
     * llegan al insert, y decide el índice único: una gana y la otra se encuentra el correo ya
     * tomado. La barrera hace que arranquen juntas, pero el resultado no depende del orden en que
     * lleguen.
     */
    @Test
    void dosAltasSimultaneasConElMismoCorreoDejanUnaSolaCuenta() throws Exception {
        String email = uniqueEmail("ana");
        CyclicBarrier salida = new CyclicBarrier(2);
        Callable<Object> alta = () -> {
            salida.await(10, TimeUnit.SECONDS);
            try {
                return service.create(nombre("Ana " + UUID.randomUUID()), email, HASH);
            } catch (EmailAlreadyRegisteredException rechazada) {
                return rechazada;
            }
        };

        Future<Object> primera = hilos.submit(alta);
        Future<Object> segunda = hilos.submit(alta);
        List<Object> resultados = List.of(
                primera.get(20, TimeUnit.SECONDS), segunda.get(20, TimeUnit.SECONDS));

        assertThat(resultados).filteredOn(Registration.class::isInstance).hasSize(1);
        assertThat(resultados).filteredOn(EmailAlreadyRegisteredException.class::isInstance).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from users where upper(email) = upper(?)", Integer.class, email))
                .isEqualTo(1);
        // La perdedora creó su hogar antes de que fallara el usuario: tiene que haberlo revertido.
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from households where name like ?", Integer.class, "Casa de " + marca + "%"))
                .isEqualTo(1);
    }

    private String nombre(String persona) {
        return marca + " " + persona;
    }

    private Integer hogaresDe(String persona) {
        return jdbcTemplate.queryForObject(
                "select count(*) from households where name = ?", Integer.class, "Casa de " + nombre(persona));
    }
}
