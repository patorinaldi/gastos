package io.github.patorinaldi.gastos.api.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Contratos de M1, identidad y acceso (RF-01 a RF-07).
 *
 * <p>Ninguna respuesta incluye el hash de la contraseña ni el hash de un token: lo que la base
 * guarda para verificar no sirve para nada del lado del cliente, y exponerlo solo agranda la
 * superficie (RNF-09).
 */
public final class AuthContracts {

    private AuthContracts() {
    }

    /**
     * El alta crea también el hogar y siembra su catálogo, en una sola transacción (RN-11).
     *
     * <p>El máximo de 72 bytes de la contraseña no es arbitrario: bcrypt ignora lo que pase de ahí,
     * así que aceptar más haría creer que una contraseña más larga protege más (RNF-05).
     */
    public record RegisterRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    /** La cuenta nace sin verificar y no puede iniciar sesión hasta confirmarse (RN-02). */
    public record RegisterResponse(UUID userId, UUID householdId, boolean emailVerified) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    /** El token de sesión no se guarda en la base: el servidor lo firma y lo acepta hasta que vence. */
    public record LoginResponse(String token, Instant expiresAt) {
    }

    /**
     * La respuesta es la misma exista o no el correo, para no permitir enumerar cuentas (RN-03).
     */
    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    /** El hogar sale del usuario, no de la petición: un cliente no tiene forma de nombrar otro. */
    public record CurrentUserResponse(
            UUID userId,
            String name,
            String email,
            boolean emailVerified,
            UUID householdId,
            String householdName) {
    }

    /** El nombre es para poder revocar el correcto cuando hay varios dispositivos. */
    public record CreateMachineTokenRequest(@NotBlank @Size(max = 100) String name) {
    }

    /**
     * Única respuesta que incluye el token en claro. La base guarda solo su hash, así que si se
     * pierde no hay forma de recuperarlo: se emite otro y se revoca este.
     */
    public record CreatedMachineTokenResponse(UUID id, String name, String token, Instant createdAt) {
    }

    public record MachineTokenResponse(
            UUID id,
            String name,
            Instant createdAt,
            Instant lastUsedAt,
            Instant revokedAt) {
    }
}
