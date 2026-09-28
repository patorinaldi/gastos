package io.github.patorinaldi.gastos.api.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Contratos de M4, categorización (RF-19 a RF-24, RF-43).
 *
 * <p>El catálogo que devuelve la API es el de las categorías activas. Una categoría dada de baja
 * deja de ofrecerse pero sigue apareciendo como categoría de los gastos viejos que la usaban
 * (RN-16), así que el nombre que trae {@code ExpenseResponse} puede ser el de una categoría que ya
 * no está en este listado.
 */
public final class CategoryContracts {

    private CategoryContracts() {
    }

    public record CategoryResponse(UUID id, String name) {
    }

    /** Sirve para el alta y para el renombrado: en los dos casos lo único que cambia es el nombre. */
    public record SaveCategoryRequest(@NotBlank @Size(max = 200) String name) {
    }

    public record CategoryRuleResponse(UUID id, String pattern, UUID categoryId, String categoryName) {
    }

    /**
     * El patrón se guarda normalizado, en minúscula y sin tildes. Dos reglas con el mismo patrón en
     * un hogar se rechazan con 409 (RN-10).
     */
    public record CreateCategoryRuleRequest(
            @NotBlank @Size(max = 200) String pattern,
            @NotNull UUID categoryId) {
    }

    /**
     * {@code reclassified} es cuántos gastos salieron de la bandeja al crear la regla (RN-13). Es
     * el dato que la interfaz muestra para confirmar que enseñar un comercio sirvió de algo, y la
     * razón por la que el alta de una regla no devuelve solo el identificador.
     */
    public record CreateCategoryRuleResponse(
            UUID id,
            String pattern,
            UUID categoryId,
            int reclassified) {
    }
}
