package io.github.patorinaldi.gastos.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Token de un solo uso para verificar el correo o restablecer la contraseña (V5, RN-03).
 *
 * <p>Guarda el hash SHA-256 del token, nunca el token: el valor en claro existe solo en el correo
 * enviado. La creación y el vencimiento los pone la base, con sus valores por defecto: así el
 * vencimiento de 24 horas vive en un solo lugar y se compara siempre contra el reloj de la base.
 * Usarlo es un update condicional del repositorio, no un setter, para que dos usos simultáneos no
 * puedan pasar los dos. Sobre el uso de Lombok, ver {@link Household}.
 */
@Entity
@Table(name = "auth_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "purpose", nullable = false, updatable = false)
    private AuthTokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private byte[] tokenHash;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, insertable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at", insertable = false, updatable = false)
    private Instant usedAt;

    public AuthToken(UUID userId, AuthTokenPurpose purpose, byte[] tokenHash) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.purpose = Objects.requireNonNull(purpose, "purpose");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash").clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthToken that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
