package io.github.patorinaldi.gastos.api.service.category;

import io.github.patorinaldi.gastos.api.domain.CategoryRule;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * El motor de categorización (RF-20, RF-21): las reglas de un hogar, listas para resolver la categoría
 * de un comercio. Es determinista: la misma entrada da siempre el mismo resultado, sin aprendizaje
 * automático ni servicios externos.
 *
 * <p>Las reglas se ordenan una sola vez por prioridad, y la categoría es la de la primera que coincide
 * (RN-17): la de patrón más largo, contando letras y números; a igual longitud, la más antigua; y a
 * igual fecha, como las 43 de la V3 que se siembran en la misma transacción, la de menor
 * identificador, para que el resultado nunca dependa del orden en que llegan de la base.
 *
 * <p>Recibe solo reglas de categorías activas (RN-16): ver
 * {@code CategoryRuleRepository#findAllOfActiveCategories}.
 */
public final class RuleSet {

    private static final Comparator<Candidate> PRIORITY = Comparator
            .comparingInt((Candidate c) -> c.key().length()).reversed()
            .thenComparing(Candidate::createdAt)
            .thenComparing(Candidate::id);

    private final List<Candidate> candidates;

    private RuleSet(List<Candidate> candidates) {
        this.candidates = candidates;
    }

    public static RuleSet of(Collection<CategoryRule> rules) {
        return new RuleSet(rules.stream()
                .map(r -> new Candidate(r.getId(), Patterns.matchKey(r.getPattern()), r.getCategoryId(), r.getCreatedAt()))
                // Una clave vacía estaría contenida en cualquier comercio. La API no deja crearla; esto
                // cubre una regla escrita por fuera de ella.
                .filter(c -> !c.key().isEmpty())
                .sorted(PRIORITY)
                .toList());
    }

    /** La categoría que corresponde al comercio, o vacío si ninguna regla coincide (RN-12). */
    public Optional<UUID> categoryFor(String merchant) {
        String merchantKey = Patterns.matchKey(merchant);
        return candidates.stream()
                .filter(c -> merchantKey.contains(c.key()))
                .map(Candidate::categoryId)
                .findFirst();
    }

    private record Candidate(UUID id, String key, UUID categoryId, Instant createdAt) {
    }
}
