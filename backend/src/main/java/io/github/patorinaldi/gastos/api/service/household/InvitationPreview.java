package io.github.patorinaldi.gastos.api.service.household;

/**
 * Lo que pasaría al canjear un código, sin aplicarlo: a qué hogar se suma, si el actual queda
 * archivado y cuántas automatizaciones dejan de funcionar (RN-18). Ver {@link HouseholdView}.
 */
public record InvitationPreview(
        String householdName,
        boolean currentHouseholdWillBeArchived,
        int machineTokensToRevoke) {
}
