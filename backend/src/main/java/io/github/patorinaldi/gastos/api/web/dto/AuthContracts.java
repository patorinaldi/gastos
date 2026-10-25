package io.github.patorinaldi.gastos.api.web.dto;

import io.github.patorinaldi.gastos.api.web.dto.validation.DisplayNames;
import io.github.patorinaldi.gastos.api.web.dto.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Contratos de M1, identidad y acceso (RF-01 a RF-07).
 *
 * <p>Ninguna respuesta incluye el hash de la contraseña ni el hash de un token: lo que la base
 * guarda para verificar no sirve para nada del lado del cliente, y exponerlo solo agranda la
 * superficie (RNF-09).
 *
 * <p>Toda contraseña que entra lleva un máximo de 72 bytes UTF-8, que es donde bcrypt deja de leer
 * (RNF-05). El límite va en bytes y no en caracteres porque una contraseña con tildes o eñes ocupa
 * más de un byte por carácter: medida en caracteres pasaría el control y después bcrypt la
 * truncaría sin avisar, o la biblioteca fallaría y la petición volvería como 500.
 */
public final class AuthContracts {

    /** Lo que bcrypt alcanza a leer. */
    private static final int BCRYPT_MAX_BYTES = 72;

    private AuthContracts() {
    }

    /**
     * El alta crea también el hogar y siembra su catálogo, en una sola transacción (RN-11).
     *
     * <p>El nombre se valida con {@link DisplayNames}: sin caracteres de control ni invisibles.
     */
    public record RegisterRequest(
            @NotBlank @Size(max = 200)
            @Pattern(regexp = DisplayNames.PATTERN, message = DisplayNames.MESSAGE)
            String name,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8) @MaxUtf8Bytes(BCRYPT_MAX_BYTES) String password) {
    }

    /** La cuenta nace sin verificar y no puede iniciar sesión hasta confirmarse (RN-02). */
    public record RegisterResponse(UUID userId, UUID householdId, boolean emailVerified) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    /**
     * La contraseña lleva máximo pero no mínimo: el mínimo lo exige el alta, y acá rechazar una
     * contraseña corta solo le diría a quien prueba al azar que esa no podía ser. El máximo sí
     * hace falta, y es el único control antes de bcrypt: este punto de entrada no está autenticado,
     * así que sin él cualquiera puede hacer que el servidor cifre un texto de kilobytes por
     * petición.
     */
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @MaxUtf8Bytes(BCRYPT_MAX_BYTES) String password) {
    }

    /** El token de sesión no se guarda en la base: el servidor lo firma y lo acepta hasta que vence. */
    public record LoginResponse(String token, Instant expiresAt) {
    }

    /** La respuesta es la misma exista o no el correo, para no permitir enumerar cuentas (RN-03). */
    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 320) String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 64) String token,
            @NotBlank @Size(min = 8) @MaxUtf8Bytes(BCRYPT_MAX_BYTES) String password) {
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

    /**
     * El nombre es para poder revocar el correcto cuando hay varios dispositivos. La contraseña
     * actual se pide de nuevo: el token no vence, y sin ella una sesión robada alcanzaría para
     * convertirse en un acceso permanente.
     */
    public record CreateMachineTokenRequest(
            @NotBlank @Size(max = 100)
            @Pattern(regexp = DisplayNames.PATTERN, message = DisplayNames.MESSAGE)
            String name,
            @NotBlank @MaxUtf8Bytes(BCRYPT_MAX_BYTES) String password) {

        // Sin esto, el toString del record mostraría la contraseña si alguien lo registra en un log.
        @Override
        public String toString() {
            return "CreateMachineTokenRequest[name=" + name + "]";
        }
    }

    /**
     * Única respuesta que incluye el token en claro. La base guarda solo su hash, así que si se
     * pierde no hay forma de recuperarlo: se emite otro y se revoca este.
     */
    public record CreatedMachineTokenResponse(UUID id, String name, String token, Instant createdAt) {

        // Spring MVC registra el cuerpo de la respuesta con su toString en DEBUG y TRACE: sin esto,
        // el token quedaría en el log (RNF-09).
        @Override
        public String toString() {
            return "CreatedMachineTokenResponse[id=" + id + ", name=" + name + ", createdAt=" + createdAt + "]";
        }
    }

    public record MachineTokenResponse(
            UUID id,
            String name,
            Instant createdAt,
            Instant lastUsedAt,
            Instant revokedAt) {
    }
}
