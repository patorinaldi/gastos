package io.github.patorinaldi.gastos.api.service.capture;

import io.github.patorinaldi.gastos.api.domain.PaymentMethod;
import io.github.patorinaldi.gastos.api.security.AuthenticatedUser;
import io.github.patorinaldi.gastos.api.service.expense.AmountParser;
import io.github.patorinaldi.gastos.api.service.expense.ExpenseService;
import io.github.patorinaldi.gastos.api.service.expense.ExpenseView;
import io.github.patorinaldi.gastos.api.service.expense.InvalidAmountException;
import io.github.patorinaldi.gastos.api.service.expense.NewExpense;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * Captura rápida desde un cliente automatizado (M6, RF-30 a RF-32, CU-07).
 *
 * <p>Quien llama ya está autenticado por su token de cliente máquina: el filtro comprobó que no esté
 * revocado y que su hogar sea el hogar actual de su dueño (RN-18), y dejó al dueño como principal,
 * con el hogar del token en contexto. Desde ahí, una captura es un alta de gasto como cualquier otra,
 * con dos diferencias: el importe llega como texto sin normalizar, y la fecha es siempre la de hoy.
 * El alta resuelve la categoría con las reglas del hogar (RN-17), así que una captura y un gasto
 * cargado a mano con el mismo comercio quedan en la misma categoría.
 */
@Service
public class CaptureService {

    private final ExpenseService expenses;

    public CaptureService(ExpenseService expenses) {
        this.expenses = expenses;
    }

    /**
     * Registra el gasto a nombre del dueño del token, en su hogar.
     *
     * <p>El importe se interpreta antes de abrir la transacción del alta: uno ambiguo se rechaza sin
     * tocar la base, en lugar de registrarse con un valor adivinado (CU-07, 3a).
     *
     * @throws InvalidAmountException si el importe no se puede interpretar sin ambigüedad (RF-32, RN-06)
     */
    public ExpenseView capture(AuthenticatedUser owner, String rawAmount, String merchant, PaymentMethod paymentMethod) {
        BigDecimal amount = AmountParser.parse(rawAmount);
        return expenses.create(owner, new NewExpense(amount, merchant, null, paymentMethod, null));
    }
}
