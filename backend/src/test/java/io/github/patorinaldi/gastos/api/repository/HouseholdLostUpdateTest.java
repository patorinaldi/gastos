package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.HouseholdTestSupport;
import io.github.patorinaldi.gastos.api.domain.Household;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Renombrar y archivar tocan la misma fila de households desde transacciones distintas. Cada una
 * tiene que escribir solo lo que cambió: si el update llevara la fila entera, la que confirma
 * última pisaría a la otra con lo que leyó al empezar.
 */
class HouseholdLostUpdateTest extends HouseholdTestSupport {

    @Autowired
    private HouseholdRepository households;

    private UUID casa;

    @BeforeEach
    void sembrarHogar() {
        casa = createHousehold("Casa de Ana");
    }

    @AfterEach
    void limpiar() {
        deleteHousehold(casa);
    }

    @Test
    void renombrarNoDeshaceUnArchivadoConfirmadoEntretanto() {
        inTransaction(() -> {
            Household leido = households.findById(casa).orElseThrow();

            // Otra transacción archiva el hogar y confirma mientras esta todavía no escribió.
            requiresNew().executeWithoutResult(status -> jdbcTemplate.update(
                    "update households set active = false, archived_at = now() where id = ?", casa));

            leido.setName("Depto Palermo");
        });

        assertThat(jdbcTemplate.queryForObject(
                "select active from households where id = ?", Boolean.class, casa)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select name from households where id = ?", String.class, casa)).isEqualTo("Depto Palermo");
    }

    @Test
    void archivarNoDeshaceUnRenombreConfirmadoEntretanto() {
        inTransaction(() -> {
            Household leido = households.findById(casa).orElseThrow();

            requiresNew().executeWithoutResult(status -> jdbcTemplate.update(
                    "update households set name = 'Depto Palermo' where id = ?", casa));

            leido.archive(Instant.now());
        });

        assertThat(jdbcTemplate.queryForObject(
                "select name from households where id = ?", String.class, casa)).isEqualTo("Depto Palermo");
        assertThat(jdbcTemplate.queryForObject(
                "select active from households where id = ?", Boolean.class, casa)).isFalse();
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
