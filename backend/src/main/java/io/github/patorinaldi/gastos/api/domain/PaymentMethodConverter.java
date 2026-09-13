package io.github.patorinaldi.gastos.api.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Traduce entre la constante del enum y el valor en español que guarda la columna.
 *
 * <p>No se usa {@code @Enumerated(EnumType.STRING)} porque guardaría el nombre de la constante
 * («EFECTIVO») y el check {@code expenses_payment_method_valid} lo rechazaría.
 */
@Converter(autoApply = true)
public class PaymentMethodConverter implements AttributeConverter<PaymentMethod, String> {

    @Override
    public String convertToDatabaseColumn(PaymentMethod attribute) {
        return attribute == null ? null : attribute.databaseValue();
    }

    @Override
    public PaymentMethod convertToEntityAttribute(String dbData) {
        return dbData == null ? null : PaymentMethod.fromDatabaseValue(dbData);
    }
}
