package io.github.patorinaldi.gastos.api;

import io.github.patorinaldi.gastos.api.security.CurrentHousehold;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Base para las pruebas que necesitan alternar el hogar activo entre transacciones.
 *
 * <p>Estas pruebas <strong>no</strong> pueden llevar {@code @Transactional} a nivel de clase:
 * HouseholdTransactionListener lee el hogar en {@code afterBegin}, así que cambiar de contexto
 * exige abrir una transacción nueva. De ahí {@link #inTransaction} y {@link #withHousehold}, y
 * de ahí también que las filas que se insertan queden confirmadas y haya que borrarlas a mano.
 */
public abstract class HouseholdTestSupport extends IntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    /** Ejecuta el trabajo en una transacción propia, que es donde el listener fija el hogar. */
    protected <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    protected void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }

    /** Ejecuta el trabajo con {@code household} activo, y limpia el contexto al salir. */
    protected <T> T withHousehold(UUID household, Supplier<T> work) {
        CurrentHousehold.set(household);
        try {
            return inTransaction(work);
        } finally {
            CurrentHousehold.clear();
        }
    }

    protected void withHousehold(UUID household, Runnable work) {
        withHousehold(household, () -> {
            work.run();
            return null;
        });
    }

    /** Sin hogar en contexto: las tablas con RLS deben comportarse como si estuvieran vacías. */
    protected <T> T withoutHousehold(Supplier<T> work) {
        CurrentHousehold.clear();
        return inTransaction(work);
    }

    /**
     * app_current_household() y no current_setting: tras una transacción que fijó el hogar,
     * current_setting devuelve '' en esa conexión en lugar de null.
     */
    protected UUID currentHousehold() {
        return jdbcTemplate.queryForObject("select app_current_household()", UUID.class);
    }

    /** households no tiene RLS todavía, así que se inserta sin contexto. */
    protected UUID createHousehold(String name) {
        UUID household = UUID.randomUUID();
        jdbcTemplate.update("insert into households (id, name) values (?, ?)", household, name);
        return household;
    }

    protected UUID createUser(UUID household, String email, String name) {
        UUID user = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into users (id, household_id, email, password_hash, name)"
                        + " values (?, ?, ?, ?, ?)",
                user, household, email, "hash-irrelevante", name);
        return user;
    }

    /**
     * Borra el hogar y todo lo suyo. Las tablas con RLS se limpian con el hogar en contexto,
     * porque sin él el delete no alcanzaría ninguna fila.
     */
    protected void deleteHousehold(UUID household) {
        withHousehold(household, () -> {
            jdbcTemplate.update("delete from expenses where household_id = ?", household);
            jdbcTemplate.update("delete from category_rules where household_id = ?", household);
            jdbcTemplate.update("delete from categories where household_id = ?", household);
        });
        jdbcTemplate.update("delete from users where household_id = ?", household);
        jdbcTemplate.update("delete from households where id = ?", household);
    }
}
