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
 * Hogar: unidad de aislamiento del sistema. Todo dato de negocio pertenece a uno, y las
 * políticas RLS de la migración V2 filtran por su identificador.
 *
 * <p>Criterio de Lombok que siguen las cinco entidades:
 * <ul>
 *   <li>{@code @Getter} a nivel de clase y {@code @Setter} por campo: el id, el hogar y las
 *       marcas de tiempo no tienen por qué ser mutables;</li>
 *   <li>sin {@code @Data}, porque incluye {@code @ToString} y {@code @RequiredArgsConstructor}.
 *       Un {@code toString()} en una entidad JPA es mal default: al registrarla en un log puede
 *       disparar cargas perezosas, y vuelca todos los campos. En la mayoría de estas clases
 *       sería solo ruido; en {@link User} volcaría el hash de la contraseña, que es la razón
 *       por la que conviene no acostumbrarse a ponerlo;</li>
 *   <li>{@code equals} y {@code hashCode} escritos a mano — ver el detalle más abajo.</li>
 * </ul>
 */
@Entity
@Table(name = "households")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Setter
    // columnDefinition: la V1 declara text, no varchar. Sin esto Hibernate espera
    // varchar(255) y la validación depende de que pgjdbc reporte text como VARCHAR.
    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Household(String name) {
        this.name = name;
    }

    // equals/hashCode a mano y no con Lombok: el patrón seguro para JPA necesita que ambos
    // dependan de cosas distintas —equals del id, hashCode de nada que cambie al persistir—
    // y Lombok deriva los dos del mismo conjunto de campos, así que no puede expresarlo.
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        // Sin id todavía, dos instancias distintas nunca son iguales: si acá se comparara
        // null con null, dos entidades recién creadas y sin guardar darían iguales entre sí.
        if (!(other instanceof Household that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        // Constante a propósito: el id lo asigna JPA al persistir, y un hashCode derivado de él
        // cambiaría con la entidad ya dentro de una colección, que dejaría de encontrarla.
        return getClass().hashCode();
    }
}
