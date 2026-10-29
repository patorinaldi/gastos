package io.github.patorinaldi.gastos.api.service.category;

import java.util.UUID;

/** Una categoría del catálogo, tal como la devuelve el servicio (RNF-22). */
public record CategoryView(UUID id, String name) {
}
