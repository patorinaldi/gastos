package io.github.patorinaldi.gastos.api.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Cómo se nombra a una cuenta en el log sin escribir su correo (RNF-09).
 *
 * <p>Son los primeros 64 bits de un SHA-256: no se puede volver al correo, y alcanza para ver si
 * los eventos de un mismo log repiten cuenta. El correo se pasa a mayúsculas antes de calcularlo,
 * como lo compara la base, así que un bloqueo de login y un alta rechazada de la misma cuenta
 * muestran el mismo valor aunque la persona haya escrito el correo con otras mayúsculas.
 */
final class LogHash {

    private static final int BYTES = 8;

    private LogHash() {
    }

    static String ofEmail(String email) {
        return of(email.toUpperCase(Locale.ROOT));
    }

    static String of(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, BYTES);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 es obligatorio en toda JVM", e);
        }
    }
}
