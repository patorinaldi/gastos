package io.github.patorinaldi.gastos.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Gasto registrado por un integrante del hogar. Bajo RLS desde la migración V2.
 *
 * <p>{@code categoryId} es nulo cuando ningún patrón coincidió en el alta: son los gastos de la
 * bandeja de no categorizados. La FK compuesta de la V1 es MATCH SIMPLE, así que no se evalúa
 * mientras la categoría sea nula.
 *
 * <p>El importe es {@link BigDecimal} y nunca punto flotante, por la precisión exacta que exige
 * el dominio.
 *
 * <p>Sobre el uso de Lombok, ver {@link Household}.
 *
 * <p><strong>Baja lógica (RN-16).</strong> {@code delete} no borra la fila: {@link SQLDelete} lo
 * convierte en un update que marca {@code active = false} y registra {@code deleted_at} y
 * {@code updated_at}. {@link SQLRestriction} deja las filas dadas de baja fuera de toda lectura
 * por JPA ({@code findById}, {@code findAll} y las consultas derivadas), así que ningún repositorio
 * tiene que acordarse de filtrarlas. {@code active} y {@code deletedAt} son de solo lectura desde
 * la entidad: el único camino para cambiarlos es la baja, que los escribe juntos y no puede violar
 * el check de coherencia de la V4.
 *
 * <p>{@link SQLRestriction} está acá y no en {@link Category} a propósito. Un gasto dado de baja
 * tiene que desaparecer de todo, incluidos los totales del análisis. Una categoría dada de baja, en
 * cambio, tiene que seguir apareciendo en los gastos viejos que la usaban (ver {@link Category}).
 */
@Entity
@Table(name = "expenses")
@SQLDelete(sql = "update expenses set active = false, deleted_at = now(), updated_at = now()"
        + " where id = ?")
@SQLRestriction("active")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "household_id", nullable = false)
    private UUID householdId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Setter
    @Column(name = "category_id")
    private UUID categoryId;

    @Setter
    @Column(name = "merchant", nullable = false, length = 200)
    private String merchant;

    @Setter
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Setter
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Setter
    private PaymentMethod paymentMethod;

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

    public Expense(UUID householdId, UUID ownerId, String merchant, BigDecimal amount,
                   LocalDate expenseDate, PaymentMethod paymentMethod) {
        this.householdId = householdId;
        this.ownerId = ownerId;
        this.merchant = merchant;
        this.amount = amount;
        this.expenseDate = expenseDate;
        this.paymentMethod = paymentMethod;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Expense that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
