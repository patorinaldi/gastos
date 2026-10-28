package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.category.CategoryService;
import io.github.patorinaldi.gastos.api.service.category.CategoryView;
import io.github.patorinaldi.gastos.api.web.dto.CategoryContracts.CategoryResponse;
import io.github.patorinaldi.gastos.api.web.dto.CategoryContracts.SaveCategoryRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * M4, catálogo de categorías (RF-24, HU-12). Exige sesión, y el hogar es siempre el de quien consulta:
 * lo fija RLS. Una categoría de otro hogar responde 404, igual que una inexistente.
 *
 * <p>Traduce entre los contratos de la API y los tipos de {@link CategoryService} (RNF-22).
 */
@RestController
@RequestMapping("/api/categories")
class CategoryController {

    private final CategoryService categories;

    CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    /** Las categorías activas, por nombre: las que se ofrecen para clasificar. */
    @GetMapping
    List<CategoryResponse> list() {
        return categories.list().stream().map(CategoryController::response).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CategoryResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                            @Valid @RequestBody SaveCategoryRequest request) {
        return response(categories.create(user, request.name()));
    }

    @PatchMapping("/{id}")
    CategoryResponse rename(@AuthenticationPrincipal AuthenticatedUser user,
                            @PathVariable UUID id,
                            @Valid @RequestBody SaveCategoryRequest request) {
        return response(categories.rename(user, id, request.name()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        categories.deactivate(user, id);
    }

    private static CategoryResponse response(CategoryView category) {
        return new CategoryResponse(category.id(), category.name());
    }
}
