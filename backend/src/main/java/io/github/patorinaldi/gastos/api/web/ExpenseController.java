package io.github.patorinaldi.gastos.api.web;

import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.expense.ExpenseChanges;
import io.github.patorinaldi.gastos.api.service.expense.ExpenseCriteria;
import io.github.patorinaldi.gastos.api.service.expense.ExpenseService;
import io.github.patorinaldi.gastos.api.service.expense.ExpenseView;
import io.github.patorinaldi.gastos.api.service.expense.NewExpense;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.CreateExpenseRequest;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.ExpenseFilterRequest;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.ExpenseResponse;
import io.github.patorinaldi.gastos.api.web.dto.ExpenseContracts.UpdateExpenseRequest;
import io.github.patorinaldi.gastos.api.web.dto.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
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
 * M3, gastos (RF-13 a RF-18). Exige sesión, y el hogar es siempre el de quien consulta: lo fija RLS,
 * no un parámetro. Un gasto de otro hogar responde 404, igual que uno inexistente (HU-09).
 *
 * <p>Comparte la ruta con {@link AnalysisController}: {@code /summary}, {@code /monthly} y
 * {@code /merchants} son rutas literales y Spring las prefiere sobre {@code /{id}}.
 *
 * <p>Traduce entre los contratos de la API y los tipos de {@link ExpenseService}, que no conoce
 * {@code web.dto} (RNF-22).
 */
@RestController
@RequestMapping("/api/expenses")
class ExpenseController {

    private final ExpenseService expenses;

    ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    /** El responsable es siempre quien hace la petición (RF-14); el cuerpo no tiene forma de nombrar otro. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ExpenseResponse create(@AuthenticationPrincipal AuthenticatedUser user,
                           @Valid @RequestBody CreateExpenseRequest request) {
        return response(expenses.create(user, new NewExpense(
                request.amount(), request.merchant(), request.expenseDate(),
                PaymentMethods.toDomain(request.paymentMethod()), request.categoryId())));
    }

    @GetMapping
    PageResponse<ExpenseResponse> list(@Valid ExpenseFilterRequest filter) {
        ExpenseCriteria criteria = new ExpenseCriteria(filter.from(), filter.to(), filter.categoryUuid(),
                filter.isUncategorized(), filter.ownerId(), PaymentMethods.toDomain(filter.paymentMethod()));
        Page<ExpenseView> page = expenses.list(criteria, filter.page(), filter.size());
        return new PageResponse<>(page.getContent().stream().map(ExpenseController::response).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @GetMapping("/{id}")
    ExpenseResponse get(@PathVariable UUID id) {
        return response(expenses.get(id));
    }

    @PatchMapping("/{id}")
    ExpenseResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                           @PathVariable UUID id,
                           @Valid @RequestBody UpdateExpenseRequest request) {
        return response(expenses.update(user, id, new ExpenseChanges(
                request.amount(), request.merchant(), request.expenseDate(),
                PaymentMethods.toDomain(request.paymentMethod()), request.categoryId(), request.removesCategory())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        expenses.delete(user, id);
    }

    private static ExpenseResponse response(ExpenseView expense) {
        return new ExpenseResponse(
                expense.id(), expense.amount(), expense.merchant(), expense.expenseDate(),
                PaymentMethods.toValue(expense.paymentMethod()),
                expense.categoryId(), expense.categoryName(), expense.ownerId(), expense.ownerName(),
                expense.createdAt(), expense.updatedAt());
    }
}
