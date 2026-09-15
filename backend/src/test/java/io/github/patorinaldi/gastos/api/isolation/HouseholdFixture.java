package io.github.patorinaldi.gastos.api.isolation;

import java.util.UUID;

/**
 * Los identificadores de un hogar sembrado con una fila en cada tabla con RLS.
 *
 * <p>Los tests los usan para el caso que más importa: intentar alcanzar filas de otro hogar
 * conociendo sus ids exactos.
 */
record HouseholdFixture(UUID householdId, UUID userId, UUID categoryId, UUID ruleId,
                        UUID expenseId) {
}
