package io.github.patorinaldi.gastos.api.domain;

import java.util.Arrays;

/**
 * Medio de pago de un gasto.
 *
 * <p>Por la convención del proyecto los identificadores del código van en inglés y los valores
 * del dominio en español, así que la constante y el valor almacenado no coinciden: la columna
 * {@code expenses.payment_method} guarda «Efectivo», «Tarjeta» o «Transferencia», que es lo que
 * admite el check. La traducción la hace {@link PaymentMethodConverter}.
 */
public enum PaymentMethod {

    EFECTIVO("Efectivo"),
    TARJETA("Tarjeta"),
    TRANSFERENCIA("Transferencia");

    private final String databaseValue;

    PaymentMethod(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    /**
     * @throws IllegalArgumentException si el valor no es uno de los que admite el check,
     *         lo que solo puede pasar si alguien escribió en la tabla por fuera de la aplicación.
     */
    public static PaymentMethod fromDatabaseValue(String value) {
        return Arrays.stream(values())
                .filter(method -> method.databaseValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Medio de pago desconocido en la base: " + value));
    }
}
