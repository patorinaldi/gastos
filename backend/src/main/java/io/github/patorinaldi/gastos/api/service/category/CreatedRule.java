package io.github.patorinaldi.gastos.api.service.category;

import java.util.UUID;

/**
 * Una regla recién creada.
 *
 * @param pattern      el patrón como quedó guardado, normalizado
 * @param reclassified cuántos gastos salieron de la bandeja al crearla (RN-13)
 */
public record CreatedRule(UUID id, String pattern, UUID categoryId, int reclassified) {
}
