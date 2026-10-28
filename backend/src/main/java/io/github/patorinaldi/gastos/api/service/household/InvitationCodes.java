package io.github.patorinaldi.gastos.api.service.household;

import io.github.patorinaldi.gastos.api.service.TokenHash;
import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Códigos de invitación a un hogar (RN-05).
 *
 * <p>El código se comparte por canales que el sistema no controla —un chat, un papel, dictado— así
 * que el formato está pensado para eso: {@code GST-XXXX-XXX}, sobre un alfabeto sin los caracteres
 * que se confunden al leerlos (0/O, 1/I/L). Siete caracteres sobre 31 símbolos son unas 2,75 × 10¹⁰
 * combinaciones; ver la decisión en M2 de {@code modulos.md}.
 *
 * <p>La base guarda solo el hash SHA-256 del código normalizado: el valor en claro existe únicamente
 * en la respuesta que genera la invitación. Normalizar antes del hash hace que el código se
 * encuentre aunque se tipee en minúsculas, con espacios o sin guiones.
 */
@Component
public class InvitationCodes {

    private static final String PREFIX = "GST";
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    private final SecureRandom random = new SecureRandom();

    /** Un código nuevo, en el formato en que se muestra: {@code GST-XXXX-XXX}. */
    public String generate() {
        return PREFIX + "-" + randomChars(4) + "-" + randomChars(3);
    }

    /** Hash SHA-256, 32 bytes, del código normalizado. Es lo que se guarda y por lo que se busca. */
    public byte[] hash(String code) {
        return TokenHash.sha256(normalize(code));
    }

    // Mayúsculas, sin guiones ni espacios. El prefijo queda: un código sin él es otro código.
    private static String normalize(String code) {
        return code.toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
    }

    private String randomChars(int length) {
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(chars);
    }
}
