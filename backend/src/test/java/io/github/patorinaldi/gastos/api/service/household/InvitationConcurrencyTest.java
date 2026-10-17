package io.github.patorinaldi.gastos.api.service.household;

import io.github.patorinaldi.gastos.api.AuthTestSupport;
import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canjes simultáneos (CU-03, 6b; RN-05, RN-18).
 *
 * <p>Para que la carrera ocurra siempre y no por suerte, una conexión aparte bloquea la fila en
 * disputa antes de lanzar los canjes, espera a que los dos estén detenidos en ella y recién ahí la
 * suelta. Los dos llegan a ese punto habiendo leído el mismo estado, que es justo la situación que
 * los bloqueos del canje tienen que resolver.
 */
class InvitationConcurrencyTest extends AuthTestSupport {

    private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(10);

    @Autowired
    private HouseholdService householdService;

    @Autowired
    private DataSource dataSource;

    private final ExecutorService hilos = Executors.newFixedThreadPool(2);

    private UUID casaDeAna;
    private UUID casaDeJuan;
    private UUID casaDeCarla;
    private UUID ana;
    private UUID juan;
    private UUID carla;

    @BeforeEach
    void sembrarTresHogares() {
        casaDeAna = createHousehold("Casa de Ana");
        ana = createUser(casaDeAna, uniqueEmail("ana"), "Ana");
        casaDeJuan = createHousehold("Casa de Juan");
        juan = createUser(casaDeJuan, uniqueEmail("juan"), "Juan");
        casaDeCarla = createHousehold("Casa de Carla");
        carla = createUser(casaDeCarla, uniqueEmail("carla"), "Carla");
    }

    // Primero los hogares que alguien dejó, después los que recibieron gente.
    @AfterEach
    void limpiar() {
        hilos.shutdownNow();
        deleteHousehold(casaDeAna);
        deleteHousehold(casaDeCarla);
        deleteHousehold(casaDeJuan);
    }

    // Sin el bloqueo de la invitación, los dos leerían el código libre y los dos se sumarían.
    @Test
    void dosPersonasCanjeanElMismoCodigoYSoloUnaLoLogra() throws Exception {
        String codigo = codigoDe(juan, casaDeJuan);
        UUID invitacion = jdbcTemplate.queryForObject(
                "select id from household_invitations where household_id = ?", UUID.class, casaDeJuan);

        List<String> resultados = canjesSimultaneos(
                "select 1 from household_invitations where id = ? for update", invitacion,
                new Canje(ana, casaDeAna, codigo),
                new Canje(carla, casaDeCarla, codigo));

        assertThat(resultados).containsExactlyInAnyOrder("ok", "410");
        UUID ganadora = jdbcTemplate.queryForObject(
                "select redeemed_by from household_invitations where id = ?", UUID.class, invitacion);
        UUID perdedora = ganadora.equals(ana) ? carla : ana;
        UUID hogarDeLaPerdedora = ganadora.equals(ana) ? casaDeCarla : casaDeAna;
        assertThat(hogarDe(ganadora)).isEqualTo(casaDeJuan);
        // Nada a medias para quien perdió: sigue en su hogar, y su hogar sigue activo.
        assertThat(hogarDe(perdedora)).isEqualTo(hogarDeLaPerdedora);
        assertThat(activo(hogarDeLaPerdedora)).isTrue();
    }

    // Sin el bloqueo de los hogares, cada uno contaría al otro todavía adentro y nadie archivaría:
    // quedaría un hogar activo sin integrantes.
    @Test
    void losDosUltimosIntegrantesSeVanALaVezYElHogarQuedaArchivado() throws Exception {
        UUID bruno = createUser(casaDeAna, uniqueEmail("bruno"), "Bruno");
        String deJuan = codigoDe(juan, casaDeJuan);
        String deCarla = codigoDe(carla, casaDeCarla);

        List<String> resultados = canjesSimultaneos(
                "select 1 from households where id = ? for no key update", casaDeAna,
                new Canje(ana, casaDeAna, deJuan),
                new Canje(bruno, casaDeAna, deCarla));

        assertThat(resultados).containsExactly("ok", "ok");
        assertThat(hogarDe(ana)).isEqualTo(casaDeJuan);
        assertThat(hogarDe(bruno)).isEqualTo(casaDeCarla);
        assertThat(activo(casaDeAna)).isFalse();
    }

    // Sin el bloqueo de los hogares, Carla podría sumarse a la casa de Ana justo cuando Ana la deja
    // y la archiva, y quedaría adentro de un hogar archivado, sin poder volver a iniciar sesión.
    @Test
    void nadieQuedaAdentroDeUnHogarQueSeArchivaMientrasSeSuma() throws Exception {
        String deJuan = codigoDe(juan, casaDeJuan);
        String deAna = codigoDe(ana, casaDeAna);

        canjesSimultaneos(
                "select 1 from households where id = ? for no key update", casaDeAna,
                new Canje(ana, casaDeAna, deJuan),
                new Canje(carla, casaDeCarla, deAna));

        UUID hogarDeCarla = hogarDe(carla);
        assertThat(activo(hogarDeCarla)).isTrue();
        // Si Carla llegó primero, Ana no era la última y la casa de Ana sigue activa con Carla.
        if (hogarDeCarla.equals(casaDeAna)) {
            assertThat(activo(casaDeAna)).isTrue();
        }
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────────────────

    private record Canje(UUID usuario, UUID hogar, String codigo) {
    }

    /**
     * Bloquea la fila con {@code sqlBloqueo} desde otra conexión, lanza los canjes en paralelo,
     * espera a que estén todos detenidos en un bloqueo y la suelta. Devuelve, en el orden de los
     * canjes, {@code "ok"} o el estado del rechazo.
     */
    private List<String> canjesSimultaneos(String sqlBloqueo, UUID fila, Canje... canjes) throws Exception {
        try (Connection bloqueo = dataSource.getConnection()) {
            bloqueo.setAutoCommit(false);
            try (PreparedStatement ps = bloqueo.prepareStatement(sqlBloqueo)) {
                ps.setObject(1, fila);
                ps.executeQuery();
            }

            List<Future<String>> enCurso = List.of(canjes).stream()
                    .map(canje -> hilos.submit(() -> canjear(canje)))
                    .toList();
            esperarDetenidos(canjes.length);
            bloqueo.rollback();

            return enCurso.stream().map(InvitationConcurrencyTest::resultado).toList();
        }
    }

    private String canjear(Canje canje) {
        try {
            householdService.redeem(new AuthenticatedUser(canje.usuario(), canje.hogar()), canje.codigo());
            return "ok";
        } catch (InvitationUnavailableException ex) {
            return "410";
        } catch (AlreadyInHouseholdException ex) {
            return "409";
        }
    }

    // Si no se detienen —porque el bloqueo que se prueba no existe— se sigue igual al vencer la
    // espera, y la prueba falla en sus aserciones y no acá.
    private void esperarDetenidos(int cantidad) throws InterruptedException {
        Instant limite = Instant.now().plus(ESPERA_MAXIMA);
        while (Instant.now().isBefore(limite)) {
            Integer detenidos = jdbcTemplate.queryForObject(
                    "select count(*) from pg_stat_activity"
                            + " where datname = current_database() and wait_event_type = 'Lock'",
                    Integer.class);
            if (detenidos != null && detenidos >= cantidad) {
                return;
            }
            Thread.sleep(20);
        }
    }

    private static String resultado(Future<String> canje) {
        try {
            return canje.get(ESPERA_MAXIMA.toSeconds(), TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new AssertionError("El canje no terminó bien", ex);
        }
    }

    private String codigoDe(UUID usuario, UUID hogar) {
        return householdService.createInvitation(new AuthenticatedUser(usuario, hogar)).code();
    }

    private UUID hogarDe(UUID usuario) {
        return jdbcTemplate.queryForObject("select household_id from users where id = ?", UUID.class, usuario);
    }

    private boolean activo(UUID hogar) {
        return jdbcTemplate.queryForObject("select active from households where id = ?", Boolean.class, hogar);
    }
}
