package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.auth.AuthService;
import io.github.patorinaldi.gastos.api.service.auth.CurrentUser;
import io.github.patorinaldi.gastos.api.service.auth.IssuedToken;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.CurrentUserResponse;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.LoginRequest;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * M1, identidad y acceso. Por ahora, inicio de sesión y usuario autenticado; el registro, la
 * verificación de correo y el restablecimiento de contraseña se suman acá.
 *
 * <p>Traduce entre los contratos de la API y los tipos de {@link AuthService}, que no conoce
 * {@code web.dto} (RNF-22).
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        IssuedToken issued = authService.login(request.email(), request.password());
        return new LoginResponse(issued.token(), issued.expiresAt());
    }

    @GetMapping("/me")
    CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        CurrentUser current = authService.currentUser(user);
        return new CurrentUserResponse(
                current.userId(),
                current.name(),
                current.email(),
                current.emailVerified(),
                current.householdId(),
                current.householdName());
    }
}