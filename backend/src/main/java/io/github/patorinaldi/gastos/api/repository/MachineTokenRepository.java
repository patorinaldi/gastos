package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.MachineToken;
import io.github.patorinaldi.gastos.api.security.MachineTokenOwner;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * machine_tokens no tiene RLS: al autenticar una automatización, el hogar se deduce del token que
 * se está validando. Por eso cada consulta recibe el hogar explícitamente.
 */
public interface MachineTokenRepository extends JpaRepository<MachineToken, UUID> {

    /** Tokens vigentes de un usuario en un hogar: los que un cambio de hogar revocaría. */
    long countByUserIdAndHouseholdIdAndRevokedAtIsNull(UUID userId, UUID householdId);

    /**
     * Revoca los tokens vigentes de un usuario en un hogar (RN-18). Los ya revocados conservan su
     * momento de revocación. Devuelve cuántos revocó.
     */
    @Modifying
    @Query("""
            update MachineToken t set t.revokedAt = ?3
            where t.userId = ?1 and t.householdId = ?2 and t.revokedAt is null""")
    int revokeActive(UUID userId, UUID householdId, Instant at);

    /**
     * El token con ese hash, junto con el hogar actual de su dueño y si sigue activo, en una sola
     * consulta. Corre en cada petición de un cliente máquina, por el índice único
     * machine_tokens_token_hash_key. Ninguna de las tres tablas tiene RLS, así que funciona antes
     * de que haya un hogar en contexto.
     */
    @Query("""
            select new io.github.patorinaldi.gastos.api.security.MachineTokenOwner(
                t.id, t.userId, t.householdId, t.revokedAt, u.householdId, h.active)
            from MachineToken t
                join User u on u.id = t.userId
                join Household h on h.id = u.householdId
            where t.tokenHash = ?1""")
    Optional<MachineTokenOwner> findOwner(byte[] tokenHash);

    /**
     * Registra el último uso del token. La autenticación corre fuera de toda transacción, así que
     * abre la suya.
     */
    @Transactional
    @Modifying
    @Query("update MachineToken t set t.lastUsedAt = ?2 where t.id = ?1")
    int markUsed(UUID tokenId, Instant at);
}
