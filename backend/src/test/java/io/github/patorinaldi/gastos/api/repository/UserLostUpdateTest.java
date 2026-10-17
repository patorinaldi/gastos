package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.HouseholdTestSupport;
import io.github.patorinaldi.gastos.api.domain.User;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cambiarse de hogar (RN-18), invalidar las sesiones (RNF-29) y editar los datos de la cuenta tocan
 * la misma fila de users desde transacciones distintas. Cada una tiene que escribir solo lo que
 * cambió: con la fila entera, la que confirma última pisaría a la otra con lo que leyó al empezar.
 */
class UserLostUpdateTest extends HouseholdTestSupport {

    @Autowired
    private UserRepository users;

    private UUID casa;
    private UUID otraCasa;
    private UUID ana;

    @BeforeEach
    void sembrarUsuario() {
        casa = createHousehold("Casa de Ana");
        otraCasa = createHousehold("Casa de Juan");
        ana = createUser(casa, "ana-lost-" + UUID.randomUUID() + "@example.test", "Ana");
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
        deleteHousehold(otraCasa);
    }

    // Sin esto, invalidar las sesiones devolvería al usuario al hogar que dejó.
    @Test
    void invalidarLasSesionesNoDeshaceUnCambioDeHogarConfirmadoEntretanto() {
        inTransaction(() -> {
            User leido = users.findById(ana).orElseThrow();

            requiresNew().executeWithoutResult(status -> jdbcTemplate.update(
                    "update users set household_id = ? where id = ?", otraCasa, ana));

            leido.invalidateSessions();
        });

        assertThat(jdbcTemplate.queryForObject(
                "select household_id from users where id = ?", UUID.class, ana)).isEqualTo(otraCasa);
        assertThat(jdbcTemplate.queryForObject(
                "select session_version from users where id = ?", Integer.class, ana)).isEqualTo(1);
    }

    // Sin esto, editar la cuenta reviviría las sesiones que se acababan de cerrar.
    @Test
    void editarLaCuentaNoDeshaceUnaInvalidacionDeSesionesConfirmadaEntretanto() {
        inTransaction(() -> {
            User leido = users.findById(ana).orElseThrow();

            requiresNew().executeWithoutResult(status -> jdbcTemplate.update(
                    "update users set session_version = session_version + 1 where id = ?", ana));

            leido.setName("Ana María");
        });

        assertThat(jdbcTemplate.queryForObject(
                "select session_version from users where id = ?", Integer.class, ana)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select name from users where id = ?", String.class, ana)).isEqualTo("Ana María");
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
