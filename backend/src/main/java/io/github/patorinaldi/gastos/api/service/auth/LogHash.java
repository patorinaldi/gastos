package io.github.patorinaldi.gastos.api.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cómo se nombra a una cuenta en el log sin escribir su correo (RNF-09).
 *
 * <p>Son los primeros 64 bits de un HMAC-SHA256 con una clave aleatoria que se genera al arrancar y
 * no sale de la memoria. Un hash sin clave se podría revertir: con una lista de correos filtrados
 * alcanza con calcular el hash de cada uno y buscarlo en el log. Con la clave, el valor solo sirve
 * para ver si los eventos de una misma ejecución repiten cuenta; después de un reinicio, la misma
 * cuenta da otro valor.
 *
 * <p>El correo se pasa a mayúsculas antes de calcularlo, como lo compara la base, así que un bloqueo
 * de login y un alta rechazada de la misma cuenta muestran el mismo valor aunque la persona haya
 * escrito el correo con otras mayúsculas.
 */
final class LogHash {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int BYTES = 8;
    private static final SecretKeySpec KEY = randomKey();

    private LogHash() {
    }

    static String ofEmail(String email) {
        return of(email.toUpperCase(Locale.ROOT));
    }

    static String of(String value) {
        try {
            // Mac no es seguro entre hilos: uno por llamada.
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(KEY);
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 es obligatorio en toda JVM", e);
        }
    }

    private static SecretKeySpec randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return new SecretKeySpec(key, ALGORITHM);
    }
}
