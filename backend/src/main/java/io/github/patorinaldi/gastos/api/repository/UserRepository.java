package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.User;
import io.github.patorinaldi.gastos.api.security.SessionUser;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * users todavía no tiene RLS: el login necesita resolver el email antes de que exista contexto
 * de hogar. Por eso las consultas que listan usuarios reciben el hogar explícitamente.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Búsqueda de login. Case-insensitive: Spring Data JPA traduce IgnoreCase a
     * {@code upper(email) = upper(?1)}, por eso el índice único
     * {@code users_email_key} (V2.5) está sobre {@code upper(email)}.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * El hogar actual del usuario y si sigue activo, en una sola consulta. Corre en cada petición
     * autenticada: el hogar se lee de acá y no del token (RN-18). Ninguna de las dos tablas tiene
     * RLS, así que funciona antes de que haya un hogar en contexto.
     */
    @Query("""
            select new io.github.patorinaldi.gastos.api.security.SessionUser(u.id, u.householdId, h.active)
            from User u join Household h on h.id = u.householdId
            where u.id = ?1""")
    Optional<SessionUser> findSessionUser(UUID userId);

    List<User> findByHouseholdIdOrderByNameAsc(UUID householdId);
}
