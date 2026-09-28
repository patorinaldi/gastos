package io.github.patorinaldi.gastos.api.web.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Medio de pago tal como viaja en la API: {@code "Efectivo"}, {@code "Tarjeta"} o
 * {@code "Transferencia"} (RD-05, RD-06).
 *
 * <p>Es un tipo aparte de {@code domain.PaymentMethod} a propósito, como el resto de los DTOs: el
 * modelo interno no se expone en la API. La traducción entre ambos la hace la capa de servicio,
 * igual que {@code PaymentMethodConverter} traduce entre el enumerado y la columna.
 *
 * <p>El conjunto es cerrado y lo sostiene un check en la base, así que agregar un medio de pago
 * exige una migración: acá, un valor desconocido no liga y la petición se rechaza con 400.
 */
public enum PaymentMethodValue {

    EFECTIVO("Efectivo"),
    TARJETA("Tarjeta"),
    TRANSFERENCIA("Transferencia");

    private final String label;

    PaymentMethodValue(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    @JsonCreator
    public static PaymentMethodValue from(String value) {
        return Arrays.stream(values())
                .filter(method -> method.label.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Medio de pago no admitido: " + value));
    }
}
