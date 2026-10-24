package io.github.patorinaldi.gastos.api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hash con el que se guardan y se buscan los valores secretos de un solo dueño: tokens de cliente
 * máquina, códigos de invitación y tokens de verificación y restablecimiento. Las tres tablas
 * exigen los 32 bytes de un SHA-256.
 *
 * <p>Cómo se genera cada valor sí cambia de uno a otro (prefijo, alfabeto, normalización), y queda
 * en cada clase. Lo común es el hash: un solo lugar para cambiarlo.
 */
public final class TokenHash {

    private TokenHash() {
    }

    /** SHA-256, 32 bytes, de {@code value} en UTF-8. */
    public static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 es obligatorio en toda JVM", e);
        }
    }
}
