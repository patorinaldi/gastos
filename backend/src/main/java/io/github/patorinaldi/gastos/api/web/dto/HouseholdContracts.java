package io.github.patorinaldi.gastos.api.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Contratos de M2, hogares (RF-08 a RF-12).
 *
 * <p>Ninguna ruta lleva el identificador del hogar: el del usuario autenticado es el único al que
 * puede acceder. El código de invitación viaja en el cuerpo y nunca en la ruta, para que no quede
 * registrado en los logs de acceso del servidor.
 */
public final class HouseholdContracts {

    private HouseholdContracts() {
    }

    public record HouseholdResponse(UUID id, String name, Instant createdAt) {
    }

    public record RenameHouseholdRequest(@NotBlank @Size(max = 200) String name) {
    }

    public record MemberResponse(UUID id, String name, String email) {
    }

    /** El código en claro existe solo acá: la base guarda su hash. */
    public record InvitationResponse(String code, Instant expiresAt) {
    }

    public record InvitationPreviewRequest(@NotBlank String code) {
    }

    /**
     * Canjear un código es cambiarse de hogar, porque todo usuario ya tiene uno (RN-11). Esta
     * respuesta es lo que la interfaz necesita para advertir antes de aplicarlo (RN-18): qué hogar
     * es, si el actual queda archivado por quedarse sin integrantes, y cuántas automatizaciones
     * dejan de funcionar.
     */
    public record InvitationPreviewResponse(
            String householdName,
            boolean currentHouseholdWillBeArchived,
            int machineTokensToRevoke) {
    }

    /**
     * {@code confirm} tiene que venir en true. No alcanza con que la interfaz haya llamado antes a
     * la vista previa: el canje repite todas las validaciones y exige la confirmación explícita,
     * porque deja los gastos cargados en el hogar anterior y eso no se deshace.
     */
    public record RedeemInvitationRequest(
            @NotBlank String code,
            @AssertTrue(message = "El canje exige confirmación explícita") boolean confirm) {
    }

    public record RedeemInvitationResponse(
            UUID householdId,
            String householdName,
            boolean previousHouseholdArchived) {
    }
}
