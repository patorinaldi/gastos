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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Categoría de gasto, propia de cada hogar. Bajo RLS desde la migración V2.
 *
 * <p>Sobre el uso de Lombok, ver {@link Household}.
 *
 * <p><strong>Baja lógica (RN-16).</strong> {@code delete} marca la fila como en {@link Expense},
 * pero, a diferencia de ella, esta entidad <em>no</em> lleva {@code @SQLRestriction}. Los gastos
 * conservan su categoría dada de baja en el historial y en el análisis. Una restricción en la
 * entidad Hibernate la aplica también a los joins ({@code left join categories c on (c.active)
 * ...}), y un gasto de agosto quedaría sin categoría, o fuera del total, después de dar de baja su
 * categoría en septiembre. Por eso las activas se filtran solo donde se ofrecen categorías (ver
 * {@code CategoryRepository}), y {@code findById} y los joins ven también las dadas de baja.
 *
 * <p>Al dar de baja una categoría, un trigger de la V4 elimina físicamente sus reglas. Las
 * entidades {@link CategoryRule} que ya estén cargadas en el contexto de persistencia no se
 * enteran. Sobre el estado en memoria después del {@code delete}, ver {@link Expense}.
 */
@Entity
@Table(name = "categories")
@SQLDelete(sql = "update categories set active = false, deleted_at = now(), updated_at = now()"
        + " where id = ?")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Setter
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "active", nullable = false, insertable = false, updatable = false)
    private boolean active = true;

    @Column(name = "deleted_at", insertable = false, updatable = false)
    private Instant deletedAt;

    public Category(UUID householdId, String name) {
        this.householdId = householdId;
        this.name = name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Category that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
