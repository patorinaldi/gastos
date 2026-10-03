package io.github.patorinaldi.gastos.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

// gastos.email.sender y gastos.jwt.secret no tienen valor por defecto (ver application.yaml):
// sin fijarlas acá, el contexto de las pruebas no arranca. Se fijan las propiedades y no el
// perfil dev para que estas pruebas no arrastren el resto de la configuración de desarrollo.
@SpringBootTest(properties = {
        "gastos.email.sender=console",
        "gastos.jwt.secret=clave-de-pruebas-de-integracion-32+"
})
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
public abstract class IntegrationTest {
}