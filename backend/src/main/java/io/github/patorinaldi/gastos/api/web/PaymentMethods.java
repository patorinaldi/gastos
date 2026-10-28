package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import io.github.patorinaldi.gastos.api.web.dto.PaymentMethodValue;

/**
 * Traducción entre el medio de pago de la API y el del dominio, para los controladores que usan los
 * dos.
 *
 * <p>Los dos enumerados están separados a propósito (RNF-22). Un switch sin default no compila si a
 * uno le falta un caso; valueOf por nombre compilaría y fallaría en ejecución con un 500.
 */
final class PaymentMethods {

    private PaymentMethods() {
    }

    static PaymentMethodValue toValue(PaymentMethod method) {
        return switch (method) {
            case EFECTIVO -> PaymentMethodValue.EFECTIVO;
            case TARJETA -> PaymentMethodValue.TARJETA;
            case TRANSFERENCIA -> PaymentMethodValue.TRANSFERENCIA;
        };
    }

    /** Nulo si no viene: en los filtros y en las ediciones parciales, nulo es "sin cambio". */
    static PaymentMethod toDomain(PaymentMethodValue value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case EFECTIVO -> PaymentMethod.EFECTIVO;
            case TARJETA -> PaymentMethod.TARJETA;
            case TRANSFERENCIA -> PaymentMethod.TRANSFERENCIA;
        };
    }
}
