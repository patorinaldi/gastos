package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.auth.AuthService;
import io.github.patorinaldi.gastos.api.service.auth.CurrentUser;
import io.github.patorinaldi.gastos.api.service.auth.IssuedToken;
import io.github.patorinaldi.gastos.api.service.auth.Registration;
import io.github.patorinaldi.gastos.api.service.auth.RegistrationService;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.CurrentUserResponse;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.LoginRequest;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.LoginResponse;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.RegisterRequest;
import io.github.patorinaldi.gastos.api.web.dto.AuthContracts.RegisterResponse;
import jakarta.servlet.http.HttpServletRequest;
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
 * M1, identidad y acceso. Por ahora, alta de cuenta, inicio de sesión y usuario autenticado; la
 * verificación de correo y el restablecimiento de contraseña se suman acá.
 *
 * <p>Traduce entre los contratos de la API y los tipos de {@link AuthService}, que no conoce
 * {@code web.dto} (RNF-22).
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;

    AuthController(AuthService authService, RegistrationService registrationService) {
        this.authService = authService;
        this.registrationService = registrationService;
    }

    // getRemoteAddr es la dirección del cliente y no la del proxy de Azure porque
    // server.forward-headers-strategy la toma de X-Forwarded-For. Sin eso, todos los intentos
    // parecerían llegar desde el proxy y compartirían el límite por dirección. Vale para el alta y
    // para el login.

    /** RF-01. La cuenta queda creada pero sin verificar: todavía no puede iniciar sesión (RN-02). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    RegisterResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        String clientIp = ClientAddress.withoutPort(http.getRemoteAddr());
        Registration registration =
                registrationService.register(request.name(), request.email(), request.password(), clientIp);
        return new RegisterResponse(
                registration.userId(), registration.householdId(), registration.emailVerified());
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String clientIp = ClientAddress.withoutPort(http.getRemoteAddr());
        IssuedToken issued = authService.login(request.email(), request.password(), clientIp);
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