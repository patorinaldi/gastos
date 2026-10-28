package io.github.patorinaldi.gastos.api.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogHashTest {

    @Test
    void elMismoCorreoDaElMismoValorSinImportarLasMayusculas() {
        assertThat(LogHash.ofEmail("Ana@Ejemplo.test")).isEqualTo(LogHash.ofEmail("ana@ejemplo.test"));
    }

    @Test
    void correosDistintosDanValoresDistintosDeSesentaYCuatroBits() {
        assertThat(LogHash.ofEmail("ana@ejemplo.test"))
                .hasSize(16)
                .isNotEqualTo(LogHash.ofEmail("beto@ejemplo.test"));
    }

    // Sin clave, cualquiera con una lista de correos podría calcular el valor y buscarlo en el log.
    @Test
    void noEsElSha256DelCorreo() throws Exception {
        String email = "ANA@EJEMPLO.TEST";
        byte[] sha256 = MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8));

        assertThat(LogHash.of(email)).isNotEqualTo(HexFormat.of().formatHex(sha256, 0, 8));
    }
}
