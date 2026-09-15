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
 * Regla patrón → categoría del motor de categorización determinista. Bajo RLS desde la migración V2.
 *
 * <p>Sobre el uso de Lombok, ver {@link Household}.
 */
@Entity
@Table(name = "category_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Setter
    @Column(name = "pattern", nullable = false, length = 200)
    private String pattern;

    @Setter
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CategoryRule(UUID householdId, String pattern, UUID categoryId) {
        this.householdId = householdId;
        this.pattern = pattern;
        this.categoryId = categoryId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CategoryRule that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
