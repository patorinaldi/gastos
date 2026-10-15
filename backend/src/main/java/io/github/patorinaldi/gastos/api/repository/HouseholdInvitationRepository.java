package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.HouseholdInvitation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * household_invitations no tiene RLS: quien canjea un código todavía no integra el hogar que lo
 * emitió. Las búsquedas son por el hash del código, nunca por un listado del hogar.
 */
public interface HouseholdInvitationRepository extends JpaRepository<HouseholdInvitation, UUID> {

    /** Por el índice único household_invitations_code_hash_key. */
    Optional<HouseholdInvitation> findByCodeHash(byte[] codeHash);
}
