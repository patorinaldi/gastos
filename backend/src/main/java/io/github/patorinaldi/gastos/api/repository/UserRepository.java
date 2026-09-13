package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * users todavía no tiene RLS: el login necesita resolver el email antes de que exista contexto
 * de hogar. Por eso las consultas que listan usuarios reciben el hogar explícitamente.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Búsqueda de login. Case-insensitive para apoyarse en el índice único
     * {@code users_email_key} sobre {@code lower(email)}.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByHouseholdIdOrderByNameAsc(UUID householdId);
}
