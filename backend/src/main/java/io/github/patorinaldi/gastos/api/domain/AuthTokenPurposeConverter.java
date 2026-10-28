package io.github.patorinaldi.gastos.api.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Mismo criterio que {@link PaymentMethodConverter}: la columna guarda el valor del check. */
@Converter(autoApply = true)
public class AuthTokenPurposeConverter implements AttributeConverter<AuthTokenPurpose, String> {

    @Override
    public String convertToDatabaseColumn(AuthTokenPurpose attribute) {
        return attribute == null ? null : attribute.databaseValue();
    }

    @Override
    public AuthTokenPurpose convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AuthTokenPurpose.fromDatabaseValue(dbData);
    }
}
