package io.github.patorinaldi.gastos.api.web.dto;

import java.util.List;

/**
 * Página de resultados. Se devuelve un tipo propio y no el {@code Page} de Spring Data porque ese
 * serializa su estructura interna: el contrato quedaría atado a la biblioteca de acceso a datos.
 *
 * @param content       los elementos de esta página
 * @param page          número de página, empezando en 0
 * @param size          tamaño pedido, como máximo 100 (RNF-12)
 * @param totalElements total de elementos que cumplen el filtro
 * @param totalPages    total de páginas para ese tamaño
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
