package io.github.patorinaldi.gastos.api.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Valores de los tokens de cliente máquina (RF-07, RF-31).
 *
 * <p>Formato: {@code gst_} seguido de 32 bytes aleatorios en base64url sin relleno, 47 caracteres en
 * total. Los 256 bits hacen inútil probar valores, así que no hace falta un límite de intentos como
 * el de los códigos de invitación. El prefijo permite reconocer un token a simple vista, y que los
 * escáneres de secretos lo detecten si alguien lo sube a un repositorio.
 *
 * <p>La base guarda solo el hash SHA-256 del valor: el token en claro existe únicamente en la
 * respuesta que lo emite. Lo usan la emisión, para guardar el hash, y la autenticación, para
 * buscarlo.
 */
@Component
public class MachineTokenValues {

    private static final String PREFIX = "gst_";
    private static final int RANDOM_BYTES = 32;
    private static final Pattern FORMAT = Pattern.compile("gst_[A-Za-z0-9_-]{43}");

    private final SecureRandom random = new SecureRandom();

    /** Un token nuevo, en el formato en que se entrega. */
    public String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Si el valor tiene el formato de un token. Lo que no lo tiene se rechaza sin consultar la
     * base.
     */
    public boolean isWellFormed(String value) {
        return value != null && FORMAT.matcher(value).matches();
    }

    /** Hash SHA-256, 32 bytes, del valor. Es lo que se guarda y por lo que se busca. */
    public byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException ex) {
            // Toda JVM está obligada a implementar SHA-256.
            throw new IllegalStateException(ex);
        }
    }
}