package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.household.HouseholdService;
import io.github.patorinaldi.gastos.api.service.household.HouseholdView;
import io.github.patorinaldi.gastos.api.service.household.InvitationPreview;
import io.github.patorinaldi.gastos.api.service.household.IssuedInvitation;
import io.github.patorinaldi.gastos.api.service.household.Redemption;
import io.github.patorinaldi.gastos.api.web.dto.HouseholdContracts.HouseholdResponse;
import io.github.patorinaldi.gastos.api.web.dto.HouseholdContracts.InvitationPreviewRequest;
import io.github.patorinaldi.gastos.api.web.dto.HouseholdContracts.InvitationPreviewResponse;
import io.github.patorinaldi.gastos.api.web.dto.HouseholdContracts.InvitationResponse;
import io.github.patorinaldi.gastos.api.web.dto.HouseholdContracts.MemberResponse;
import io.github.patorinaldi.gastos.api.web.dto.HouseholdContracts.RedeemInvitationRequest;
import io.github.patorinaldi.gastos.api.web.dto.HouseholdContracts.RedeemInvitationResponse;
import io.github.patorinaldi.gastos.api.web.dto.HouseholdContracts.RenameHouseholdRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * M2, hogares. El recurso es {@code /api/household}, en singular y sin identificador: el hogar del
 * usuario autenticado es el único al que puede acceder, y nombrarlo en la ruta sugeriría que se
 * puede pedir otro.
 *
 * <p>Traduce entre los contratos de la API y los tipos de {@link HouseholdService}, que no conoce
 * {@code web.dto} (RNF-22).
 */
@RestController
@RequestMapping("/api/household")
class HouseholdController {

    private final HouseholdService householdService;

    HouseholdController(HouseholdService householdService) {
        this.householdService = householdService;
    }

    @GetMapping
    HouseholdResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return response(householdService.get(user));
    }

    @PatchMapping
    HouseholdResponse rename(@AuthenticationPrincipal AuthenticatedUser user,
                             @Valid @RequestBody RenameHouseholdRequest request) {
        return response(householdService.rename(user, request.name()));
    }

    @GetMapping("/members")
    List<MemberResponse> members(@AuthenticationPrincipal AuthenticatedUser user) {
        return householdService.members(user).stream()
                .map(member -> new MemberResponse(member.id(), member.name(), member.email()))
                .toList();
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationResponse createInvitation(@AuthenticationPrincipal AuthenticatedUser user) {
        IssuedInvitation invitation = householdService.createInvitation(user);
        return new InvitationResponse(invitation.code(), invitation.expiresAt());
    }

    @PostMapping("/invitations/preview")
    InvitationPreviewResponse previewInvitation(@AuthenticationPrincipal AuthenticatedUser user,
                                                @Valid @RequestBody InvitationPreviewRequest request) {
        InvitationPreview preview = householdService.preview(user, request.code());
        return new InvitationPreviewResponse(
                preview.householdName(), preview.currentHouseholdWillBeArchived(), preview.machineTokensToRevoke());
    }

    /** Sin {@code "confirm": true} responde 400 antes de llegar al servicio. */
    @PostMapping("/invitations/redeem")
    RedeemInvitationResponse redeemInvitation(@AuthenticationPrincipal AuthenticatedUser user,
                                              @Valid @RequestBody RedeemInvitationRequest request) {
        Redemption redemption = householdService.redeem(user, request.code());
        return new RedeemInvitationResponse(
                redemption.householdId(), redemption.householdName(), redemption.previousHouseholdArchived());
    }

    private static HouseholdResponse response(HouseholdView household) {
        return new HouseholdResponse(household.id(), household.name(), household.createdAt());
    }
}
