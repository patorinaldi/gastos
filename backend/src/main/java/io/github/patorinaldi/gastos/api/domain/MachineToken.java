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
import org.hibernate.annotations.CreationTimestamp;

/**
 * Token de cliente máquina (V5, RF-07): una automatización que carga gastos en un hogar a nombre de
 * un integrante.
 *
 * <p>La usan M2, que al cambiar a un usuario de hogar revoca sus tokens del hogar anterior (RN-18),
 * la autenticación de los clientes máquina, que la lee por el hash y registra su último uso, y M1,
 * que la emite y la revoca. Está mapeada completa para que {@code ddl-auto: validate} la compare
 * contra la tabla.
 *
 * <p>Guarda el hash SHA-256 del token, nunca el token. Sobre el uso de Lombok, ver
 * {@link Household}.
 */
@Entity
@Table(name = "machine_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MachineToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private byte[] tokenHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public MachineToken(UUID householdId, UUID userId, String name, byte[] tokenHash) {
        this.householdId = Objects.requireNonNull(householdId, "householdId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.name = Objects.requireNonNull(name, "name");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash").clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MachineToken that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
