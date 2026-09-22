package io.github.patorinaldi.gastos.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

// gastos.email.sender no tiene valor por defecto (ver application.yaml): sin fijarla acá, el
// contexto de las pruebas no arranca. Se fija la propiedad y no el perfil dev para que estas
// pruebas no arrastren el resto de la configuración de desarrollo.
@SpringBootTest(properties = "gastos.email.sender=console")
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
public abstract class IntegrationTest {
}
