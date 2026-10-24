package io.github.patorinaldi.gastos.api.domain;

/** Para qué sirve un token de {@code auth_tokens} (RN-03). */
public enum AuthTokenPurpose {

    EMAIL_VERIFICATION("email_verification"),
    PASSWORD_RESET("password_reset");

    private final String databaseValue;

    AuthTokenPurpose(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    /** El valor que admite el check {@code auth_tokens_purpose_valid}. */
    public String databaseValue() {
        return databaseValue;
    }

    public static AuthTokenPurpose fromDatabaseValue(String value) {
        for (AuthTokenPurpose purpose : values()) {
            if (purpose.databaseValue.equals(value)) {
                return purpose;
            }
        }
        throw new IllegalArgumentException("Propósito de token desconocido: " + value);
    }
}
