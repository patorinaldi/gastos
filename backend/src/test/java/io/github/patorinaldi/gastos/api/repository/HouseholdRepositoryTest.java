package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.IntegrationTest;
import io.github.patorinaldi.gastos.api.domain.Household;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Archivado de un hogar (RN-18): se queda sin integrantes, sus datos se conservan y nadie accede.
 */
@Transactional
class HouseholdRepositoryTest extends IntegrationTest {

    @Autowired
    private HouseholdRepository households;

    @Autowired
    private EntityManager entityManager;

    @Test
    void unHogarNuevoEstaActivo() {
        Household casa = households.saveAndFlush(new Household("Casa de Ana"));
        entityManager.clear();

        Household leido = households.findById(casa.getId()).orElseThrow();
        assertThat(leido.isActive()).isTrue();
        assertThat(leido.getArchivedAt()).isNull();
    }

    // El check households_active_archived_at_consistent rechazaría el flush si archivar fijara una
    // columna sin la otra.
    @Test
    void archivarloLoDejaInactivoConElMomentoDelArchivado() {
        Household casa = households.saveAndFlush(new Household("Casa de Ana"));
        Instant momento = Instant.now().truncatedTo(ChronoUnit.MICROS);

        casa.archive(momento);
        households.saveAndFlush(casa);
        entityManager.clear();

        Household leido = households.findById(casa.getId()).orElseThrow();
        assertThat(leido.isActive()).isFalse();
        assertThat(leido.getArchivedAt()).isEqualTo(momento);
    }
}
