package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.HouseholdInvitation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * household_invitations no tiene RLS: quien canjea un código todavía no integra el hogar que lo
 * emitió. Las búsquedas son por el hash del código, nunca por un listado del hogar.
 */
public interface HouseholdInvitationRepository extends JpaRepository<HouseholdInvitation, UUID> {

    /** Por el índice único household_invitations_code_hash_key. */
    Optional<HouseholdInvitation> findByCodeHash(byte[] codeHash);

    /**
     * Como {@link #findByCodeHash}, bloqueando la fila hasta el fin de la transacción. Dos canjes
     * del mismo código se serializan acá: el segundo la encuentra ya canjeada. Ver
     * {@code HouseholdService.redeem} por el orden de los bloqueos.
     */
    @Query(value = "select * from household_invitations where code_hash = ?1 for update", nativeQuery = true)
    Optional<HouseholdInvitation> lockByCodeHash(byte[] codeHash);
}
