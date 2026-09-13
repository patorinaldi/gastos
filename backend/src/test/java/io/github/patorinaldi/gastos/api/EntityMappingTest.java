package io.github.patorinaldi.gastos.api;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.EntityType;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El contrato de la tarea 6: el DDL de las migraciones y las entidades no divergen.
 *
 * <p>Con {@code ddl-auto: validate}, Hibernate compara ambos al construir el EntityManagerFactory
 * y aborta el arranque si no coinciden — así que el solo hecho de que este contexto levante ya es
 * la verificación. Lo que agrega este test es descartar el falso verde: si las entidades
 * estuvieran fuera del paquete escaneado, no habría nada que validar y el contexto levantaría
 * igual. Acá se comprueba que las cinco llegaron efectivamente al metamodelo.
 */
class EntityMappingTest extends IntegrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void lasCincoEntidadesDelEsquemaEstanMapeadasYValidadas() {
        Set<String> mapeadas = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(EntityType::getName)
                .collect(Collectors.toSet());

        assertThat(mapeadas).containsExactlyInAnyOrder(
                "Household", "User", "Category", "CategoryRule", "Expense");
    }
}
