package io.github.patorinaldi.gastos.api.security;

import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Fija en Postgres el hogar de CurrentHousehold al comenzar cada transacción.
 * Las políticas RLS lo leen con app_current_household().
 */
@Component
public class HouseholdTransactionListener implements TransactionExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(HouseholdTransactionListener.class);

    private final JdbcTemplate jdbcTemplate;

    public HouseholdTransactionListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void afterBegin(TransactionExecution transaction, Throwable beginFailure) {
        if (beginFailure != null) {
            return;
        }

        Optional<UUID> household = CurrentHousehold.get();
        if (household.isEmpty()) {
            log.debug("Transacción sin hogar en contexto: las tablas con RLS no devuelven filas");
            return;
        }

        try {
            jdbcTemplate.queryForObject(
                    "select set_config('app.current_household', ?, true)",
                    String.class, household.get().toString());
        } catch (DataAccessException ex) {
            // Lanzar desde acá dejaría la transacción abierta y asociada al hilo. Se marca
            // para rollback; Postgres además ya la dejó abortada, así que la próxima
            // consulta falla y el rollback sigue el camino normal.
            log.error("No se pudo fijar el hogar de la transacción", ex);
            transaction.setRollbackOnly();
        }
    }
}