package io.github.patorinaldi.gastos.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifica el arranque de la aplicacion: si el contexto no se levanta, esta prueba
 * falla y la canalizacion bloquea el PR. Es la red de seguridad de la estructura.
 */
@SpringBootTest
class GastosApplicationTests {

    @Test
    void contextLoads() {
    }

}
