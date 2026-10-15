package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.MachineToken;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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
}
