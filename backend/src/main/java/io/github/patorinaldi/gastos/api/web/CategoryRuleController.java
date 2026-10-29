package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.category.CategoryRuleService;
import io.github.patorinaldi.gastos.api.service.category.CreatedRule;
import io.github.patorinaldi.gastos.api.web.dto.CategoryContracts.CategoryRuleResponse;
import io.github.patorinaldi.gastos.api.web.dto.CategoryContracts.CreateCategoryRuleRequest;
import io.github.patorinaldi.gastos.api.web.dto.CategoryContracts.CreateCategoryRuleResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * M4, reglas patrón → categoría (RF-23, RF-43, HU-11). Exige sesión, y el hogar es siempre el de quien
 * consulta: lo fija RLS. Una regla de otro hogar responde 404, igual que una inexistente.
 *
 * <p>Traduce entre los contratos de la API y los tipos de {@link CategoryRuleService} (RNF-22).
 */
@RestController
@RequestMapping("/api/category-rules")
class CategoryRuleController {

    private final CategoryRuleService rules;

    CategoryRuleController(CategoryRuleService rules) {
        this.rules = rules;
    }

    @GetMapping
    List<CategoryRuleResponse> list() {
        return rules.list().stream()
                .map(r -> new CategoryRuleResponse(r.id(), r.pattern(), r.categoryId(), r.categoryName()))
                .toList();
    }

    /** Crea la regla y reclasifica la bandeja; la respuesta dice cuántos gastos salieron de ella. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateCategoryRuleResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                                      @Valid @RequestBody CreateCategoryRuleRequest request) {
        CreatedRule created = rules.create(user, request.pattern(), request.categoryId());
        return new CreateCategoryRuleResponse(created.id(), created.pattern(), created.categoryId(), created.reclassified());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        rules.delete(user, id);
    }
}
