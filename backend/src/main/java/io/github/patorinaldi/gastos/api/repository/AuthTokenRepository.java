package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.AuthToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * auth_tokens no tiene RLS: un token se canjea sin sesión, y por lo tanto sin hogar en contexto.
 *
 * <p>Las consultas son nativas para comparar el vencimiento con {@code now()}, el mismo reloj que
 * fijó {@code expires_at} al insertar. El propósito se pasa con
 * {@link io.github.patorinaldi.gastos.api.domain.AuthTokenPurpose#databaseValue()}.
 */
public interface AuthTokenRepository extends JpaRepository<AuthToken, UUID> {

    /** El token con ese hash y ese propósito, si no se usó ni venció. Va por auth_tokens_token_hash_key. */
    @Query(value = """
            select * from auth_tokens
            where token_hash = ?1 and purpose = ?2 and used_at is null and expires_at > now()""",
            nativeQuery = true)
    Optional<AuthToken> findUsable(byte[] tokenHash, String purpose);

    /**
     * Marca el token como usado, solo si sigue sin usar y sin vencer. Devuelve 0 si otro uso lo
     * marcó primero: el update toma el lock de la fila, así que de dos usos simultáneos el segundo
     * espera, vuelve a evaluar la condición y no la cumple.
     */
    @Modifying
    @Query(value = """
            update auth_tokens set used_at = now()
            where id = ?1 and used_at is null and expires_at > now()""",
            nativeQuery = true)
    int consume(UUID tokenId);

    /** Marca como usados todos los tokens pendientes del usuario con ese propósito. */
    @Modifying
    @Query(value = """
            update auth_tokens set used_at = now()
            where user_id = ?1 and purpose = ?2 and used_at is null""",
            nativeQuery = true)
    int consumeAll(UUID userId, String purpose);

    /**
     * Marca como usados los tokens pendientes del usuario con ese propósito, salvo {@code keptId}: el
     * que se acaba de enviar en un reenvío.
     */
    @Modifying
    @Query(value = """
            update auth_tokens set used_at = now()
            where user_id = ?1 and purpose = ?2 and used_at is null and id <> ?3""",
            nativeQuery = true)
    int consumeAllExcept(UUID userId, String purpose, UUID keptId);
}
