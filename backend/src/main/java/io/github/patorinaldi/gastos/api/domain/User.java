package io.github.patorinaldi.gastos.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Usuario. Pertenece a exactamente un hogar (la pertenencia a varios quedó fuera de alcance).
 *
 * <p>La tabla todavía no tiene RLS: el login necesita buscar por email antes de que exista un
 * contexto de hogar. Por eso las consultas de este repositorio se acotan por hogar a mano.
 *
 * <p>El caso agudo de por qué no se usa {@code @Data}: generaría un {@code toString()} que
 * incluye {@code passwordHash}, y basta con registrar la entidad en un log para filtrar el
 * hash. Ver {@link Household} para el criterio completo.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Setter
    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Setter
    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;

    @Setter
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Setter
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public User(UUID householdId, String email, String passwordHash, String name) {
        this.householdId = householdId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.emailVerified = false;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
