package io.github.patorinaldi.gastos.api.security;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fija en Postgres el hogar activo de la transacción en curso, que las políticas RLS leen con
 * {@code app_current_household()}.
 *
 * <p>Es el único lugar que conoce el nombre de la variable y su alcance. Lo usan
 * {@link HouseholdTransactionListener}, al comenzar cada transacción de una petición autenticada, y
 * el alta de una cuenta, que crea el hogar dentro de la transacción y por eso no puede esperar a
 * que el listener lo fije. Si cada uno tuviera su propia copia, cambiar una sin la otra dejaría al
 * alta escribiendo con un contexto distinto del que ve el resto de la aplicación.
 *
 * <p>El alcance es el de la transacción ({@code set_config} con {@code is_local = true}, el
 * equivalente de {@code SET LOCAL}): el valor no sobrevive al devolver la conexión al pool, que es
 * lo que impide que se filtre a la petición siguiente. Por eso hay que llamarlo dentro de una
 * transacción: fuera de una, el valor dura solo lo que la sentencia.
 */
@Component
public class HouseholdContext {

    private final JdbcTemplate jdbcTemplate;

    public HouseholdContext(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @throws org.springframework.dao.DataAccessException si Postgres rechaza el valor o la conexión
     *                                                     falla
     */
    public void bind(UUID householdId) {
        jdbcTemplate.queryForObject(
                "select set_config('app.current_household', ?, true)",
                String.class, householdId.toString());
    }
}
