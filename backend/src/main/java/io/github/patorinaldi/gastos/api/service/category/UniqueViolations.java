package io.github.patorinaldi.gastos.api.service.category;

import java.sql.SQLException;

/**
 * Reconoce la violación de un índice único por el código SQLSTATE y el nombre del índice, como
 * {@code RegistrationService}: es lo que no cambia aunque cambie la excepción con que cada capa
 * envuelve al error de la base. Cualquier otra violación de integridad es un error real y no se
 * informa como duplicado.
 */
final class UniqueViolations {

    private static final String UNIQUE_VIOLATION = "23505";

    private UniqueViolations() {
    }

    static boolean of(Throwable error, String index) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && UNIQUE_VIOLATION.equals(sql.getSQLState())
                    && sql.getMessage() != null
                    && sql.getMessage().contains(index)) {
                return true;
            }
        }
        return false;
    }
}
