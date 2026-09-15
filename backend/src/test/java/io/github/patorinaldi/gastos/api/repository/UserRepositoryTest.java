package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.IntegrationTest;
import io.github.patorinaldi.gastos.api.domain.Household;
import io.github.patorinaldi.gastos.api.domain.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * households y users todavía no están bajo RLS (la V2 las deja fuera a propósito: el login
 * resuelve el email antes de que exista contexto de hogar), así que estos tests no necesitan
 * activar ningún hogar.
 */
@Transactional
class UserRepositoryTest extends IntegrationTest {

    @Autowired
    private HouseholdRepository households;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persisteYRecuperaUnUsuarioConSuHogar() {
        Household hogar = households.saveAndFlush(new Household("Casa Rivoira"));
        User guardado = users.saveAndFlush(
                new User(hogar.getId(), "ana@example.test", "hash-irrelevante", "Ana"));

        entityManager.clear();

        Optional<User> leido = users.findById(guardado.getId());
        assertThat(leido).isPresent();
        assertThat(leido.get().getHouseholdId()).isEqualTo(hogar.getId());
        assertThat(leido.get().getName()).isEqualTo("Ana");
        // default false en la V1 y en el constructor: la cuenta no queda operativa al crearse
        assertThat(leido.get().isEmailVerified()).isFalse();
        // @CreationTimestamp lo completó al insertar
        assertThat(leido.get().getCreatedAt()).isNotNull();
    }

    @Test
    void buscaPorEmailIgnorandoMayusculas() {
        Household hogar = households.saveAndFlush(new Household("Casa Reales"));
        users.saveAndFlush(new User(hogar.getId(), "Juan@Example.test", "hash", "Juan"));
        entityManager.clear();

        // el índice único de la V1 es sobre lower(email), así que la búsqueda debe ser coherente
        assertThat(users.findByEmailIgnoreCase("juan@example.test")).isPresent();
        assertThat(users.findByEmailIgnoreCase("JUAN@EXAMPLE.TEST")).isPresent();
        assertThat(users.existsByEmailIgnoreCase("juan@example.test")).isTrue();
        assertThat(users.existsByEmailIgnoreCase("otro@example.test")).isFalse();
    }

    @Test
    void listaLosIntegrantesDeUnHogarOrdenadosPorNombre() {
        Household hogar = households.saveAndFlush(new Household("Casa Rinaldi"));
        Household otro = households.saveAndFlush(new Household("Otra casa"));
        users.saveAndFlush(new User(hogar.getId(), "pato@example.test", "hash", "Pato"));
        users.saveAndFlush(new User(hogar.getId(), "elian@example.test", "hash", "Elian"));
        users.saveAndFlush(new User(otro.getId(), "ajeno@example.test", "hash", "Ajeno"));
        entityManager.clear();

        List<User> integrantes = users.findByHouseholdIdOrderByNameAsc(hogar.getId());

        assertThat(integrantes).extracting(User::getName).containsExactly("Elian", "Pato");
    }
}
