package io.github.patorinaldi.gastos.api.service.category;

import java.util.UUID;

/** Una regla con el nombre de su categoría, tal como la devuelve el servicio (RNF-22). */
public record RuleView(UUID id, String pattern, UUID categoryId, String categoryName) {
}
