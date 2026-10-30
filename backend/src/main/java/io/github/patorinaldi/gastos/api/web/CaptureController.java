package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.auth.AuthService;
import io.github.patorinaldi.gastos.api.service.auth.CurrentUser;
import io.github.patorinaldi.gastos.api.service.capture.CaptureService;
import io.github.patorinaldi.gastos.api.service.expense.ExpenseView;
import io.github.patorinaldi.gastos.api.web.dto.CaptureContracts.CaptureConnectionResponse;
import io.github.patorinaldi.gastos.api.web.dto.CaptureContracts.CaptureRequest;
import io.github.patorinaldi.gastos.api.web.dto.CaptureContracts.CaptureResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * M6, API de integración (RF-30 a RF-32, HU-16). Se autentica con un token de cliente máquina
 * ({@code Authorization: Token ...}), no con la sesión: una sesión web acá recibe 403. El principal
 * es el dueño del token, con el hogar del token.
 */
@RestController
@RequestMapping("/api/capture")
class CaptureController {

    private final CaptureService captures;
    private final AuthService auth;

    CaptureController(CaptureService captures, AuthService auth) {
        this.captures = captures;
        this.auth = auth;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CaptureResponse capture(@AuthenticationPrincipal AuthenticatedUser owner,
                            @Valid @RequestBody CaptureRequest request) {
        ExpenseView expense = captures.capture(
                owner, request.amount(), request.merchant(), PaymentMethods.toDomain(request.paymentMethod()));
        return new CaptureResponse(
                expense.id(), expense.amount(), expense.merchant(), expense.categoryName(), expense.expenseDate());
    }

    /**
     * Verifica el token sin registrar nada. Si llega acá, el filtro ya lo aceptó; si no, responde el
     * mismo 401 que la captura. Como toda petición con token, registra su último uso.
     */
    @GetMapping("/connection")
    CaptureConnectionResponse connection(@AuthenticationPrincipal AuthenticatedUser owner) {
        CurrentUser current = auth.currentUser(owner);
        return new CaptureConnectionResponse(current.name(), current.householdName());
    }
}
