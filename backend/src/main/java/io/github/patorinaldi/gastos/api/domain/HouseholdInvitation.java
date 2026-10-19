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
 * Invitación a un hogar (V5, RN-05).
 *
 * <p>Guarda el hash SHA-256 del código, nunca el código: el valor en claro existe solo en la
 * respuesta que genera la invitación. La tabla no tiene RLS, porque quien canjea el código todavía
 * no integra el hogar que lo emitió; ver {@code modelo-de-datos.md}.
 *
 * <p>Sin setters. Lo único que cambia después de crearla es el canje, y {@link #redeem} fija quién y
 * cuándo juntos, como exige {@code household_invitations_redemption_consistent}. Sobre el uso de
 * Lombok, ver {@link Household}.
 */
@Entity
@Table(name = "household_invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HouseholdInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "household_id", nullable = false, updatable = false)
    private UUID householdId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "code_hash", nullable = false, updatable = false)
    private byte[] codeHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Lo fija la aplicación y no el default de la columna, para poder devolverlo en la respuesta
    // sin volver a leer la fila. El default de 7 días queda como respaldo de cualquier otra vía.
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "redeemed_by")
    private UUID redeemedBy;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    public HouseholdInvitation(UUID householdId, UUID createdBy, byte[] codeHash, Instant expiresAt) {
        this.householdId = householdId;
        this.createdBy = createdBy;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    public boolean isRedeemed() {
        return redeemedAt != null;
    }

    /** Vencida desde el instante mismo de su vencimiento. */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * Registra el canje. Un canje ya registrado no se sobrescribe (RN-05): lo rechaza esto y,
     * aunque se escriba sin pasar por acá, el trigger {@code household_invitations_redeemed_once}
     * (V7). Quien canjea verificó antes que la invitación siguiera libre, así que llegar acá con
     * una ya canjeada es un error de programación.
     */
    public void redeem(UUID userId, Instant at) {
        if (isRedeemed()) {
            throw new IllegalStateException("La invitación " + id + " ya fue canjeada");
        }
        this.redeemedBy = Objects.requireNonNull(userId, "userId");
        this.redeemedAt = Objects.requireNonNull(at, "at");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HouseholdInvitation that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
