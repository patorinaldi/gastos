package io.github.patorinaldi.gastos.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
public abstract class IntegrationTest {
}
