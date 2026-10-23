package io.github.patorinaldi.gastos.api.service.auth;

import io.github.patorinaldi.gastos.api.service.TokenHash;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Valores de los tokens de un solo uso que viajan por correo: verificación y restablecimiento
 * (RN-03).
 *
 * <p>Formato: 32 bytes aleatorios en base64url sin relleno, 43 caracteres que van en un enlace sin
 * escaparse. Los 256 bits hacen inútil probar valores, así que el canje no necesita un límite de
 * intentos. La base guarda solo el hash SHA-256.
 */
@Component
public class AuthTokenValues {

    private static final int RANDOM_BYTES = 32;
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lo que no tiene el formato se rechaza sin consultar la base. */
    public boolean isWellFormed(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    /** Hash SHA-256, 32 bytes. Es lo que se guarda y por lo que se busca. */
    public byte[] hash(String value) {
        return TokenHash.sha256(value);
    }
}
