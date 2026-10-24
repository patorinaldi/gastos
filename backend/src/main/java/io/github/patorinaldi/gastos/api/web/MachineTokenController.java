package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.auth.IssuedMachineToken;
import io.github.patorinaldi.gastos.api.service.auth.MachineTokenService;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.CreateMachineTokenRequest;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.CreatedMachineTokenResponse;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.MachineTokenResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * M1, tokens de cliente máquina (RF-07, HU-17). Exigen sesión: un token de cliente máquina no sirve
 * para emitir otros ni para revocarlos.
 */
@RestController
@RequestMapping("/api/auth/machine-tokens")
class MachineTokenController {

    private final MachineTokenService machineTokens;

    MachineTokenController(MachineTokenService machineTokens) {
        this.machineTokens = machineTokens;
    }

    /** La respuesta lleva el token en claro, por única vez. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedMachineTokenResponse issue(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody CreateMachineTokenRequest request) {
        IssuedMachineToken issued = machineTokens.issue(user, request.name());
        return new CreatedMachineTokenResponse(issued.id(), issued.name(), issued.token(), issued.createdAt());
    }

    @GetMapping
    List<MachineTokenResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return machineTokens.list(user).stream()
                .map(t -> new MachineTokenResponse(t.id(), t.name(), t.createdAt(), t.lastUsedAt(), t.revokedAt()))
                .toList();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        machineTokens.revoke(user, id);
    }
}
