package io.github.patorinaldi.gastos.api.repository;

import io.github.patorinaldi.gastos.api.domain.Household;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * La tabla households todavía no tiene RLS, así que
 * el acceso no está acotado por el contexto de hogar y hay que buscar siempre por id.
 */
public interface HouseholdRepository extends JpaRepository<Household, UUID> {

    /**
     * Lee y bloquea los hogares hasta el fin de la transacción, siempre en orden de id: dos
     * transacciones que bloquean los mismos hogares lo hacen en el mismo orden y no pueden quedar
     * esperándose mutuamente.
     *
     * <p>{@code for no key update} y no {@code for update}: toda escritura que referencia al hogar
     * por clave foránea —cargar un gasto, crear una categoría, mover a un usuario— toma
     * {@code for key share} sobre su fila, y {@code for update} choca con él. Con {@code for update},
     * mientras dura un canje nadie podría cargar un gasto en ninguno de los dos hogares. Archivar
     * no cambia la clave, así que este bloqueo alcanza para serializar los canjes entre sí.
     */
    @Query(value = "select * from households where id in (?1) order by id for no key update", nativeQuery = true)
    List<Household> lockAll(Collection<UUID> ids);
}
