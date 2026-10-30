import { parseAmount, toAmountInput } from './amount.ts'
import {
  UNCATEGORIZED,
  type CategoryResponse,
  type CreateExpenseRequest,
  type ExpenseResponse,
  type IsoDate,
  type Money,
  type PaymentMethod,
  type UpdateExpenseRequest,
} from './types/api.ts'
import { validateMerchant } from './validation.ts'

/*
 * La lógica del formulario de un gasto que no es dibujo: validar lo escrito y armar la petición de
 * alta o de edición. El mismo formulario sirve para las dos.
 */

export const PAYMENT_METHODS: PaymentMethod[] = ['Efectivo', 'Tarjeta', 'Transferencia']

/** Valor de la categoría en el alta cuando no se elige ninguna: la resuelven las reglas (RF-20). */
export const AUTO_CATEGORY = ''

/** Lo que tiene el formulario, tal como se escribió. */
export interface ExpenseFormValues {
  amount: string
  merchant: string
  /** Vacío en el alta: la fecha del día la pone el servidor. */
  expenseDate: string
  paymentMethod: PaymentMethod
  /**
   * El identificador de la categoría; `AUTO_CATEGORY` en el alta, para que la resuelvan las reglas;
   * `UNCATEGORIZED` en la edición, para devolver el gasto a la bandeja.
   */
  category: string
}

export interface ExpenseFormErrors {
  amount?: string
  merchant?: string
  expenseDate?: string
}

/** Lo que tiene el formulario, ya validado y en el formato de la API. */
export interface ValidExpense {
  amount: Money
  merchant: string
  expenseDate: IsoDate | undefined
  paymentMethod: PaymentMethod
  category: string
}

const DATE = /^\d{4}-\d{2}-\d{2}$/

/** Un formulario de alta vacío, con la fecha de hoy y el medio de pago por defecto del servidor. */
export function emptyExpense(today: IsoDate): ExpenseFormValues {
  return { amount: '', merchant: '', expenseDate: today, paymentMethod: 'Efectivo', category: AUTO_CATEGORY }
}

/** Un gasto cargado, como valores del formulario de edición. */
export function expenseValues(expense: ExpenseResponse): ExpenseFormValues {
  return {
    amount: toAmountInput(expense.amount),
    merchant: expense.merchant,
    expenseDate: expense.expenseDate,
    paymentMethod: expense.paymentMethod,
    category: expense.categoryId ?? UNCATEGORIZED,
  }
}

/**
 * Valida el formulario. En el alta la fecha puede quedar vacía, y la pone el servidor; en la
 * edición no, porque vacía no dice a qué fecha cambiarla.
 */
export function validateExpense(
  values: ExpenseFormValues,
  mode: 'create' | 'edit',
): { ok: true; expense: ValidExpense } | { ok: false; errors: ExpenseFormErrors } {
  const amount = parseAmount(values.amount)
  const errors: ExpenseFormErrors = {
    amount: amount.ok ? undefined : amount.error,
    merchant: validateMerchant(values.merchant),
    expenseDate: dateError(values.expenseDate, mode),
  }
  if (!amount.ok || errors.merchant || errors.expenseDate) {
    return { ok: false, errors }
  }
  return {
    ok: true,
    expense: {
      amount: amount.amount,
      merchant: values.merchant.trim(),
      expenseDate: values.expenseDate === '' ? undefined : values.expenseDate,
      paymentMethod: values.paymentMethod,
      category: values.category,
    },
  }
}

function dateError(date: string, mode: 'create' | 'edit'): string | undefined {
  if (date === '') {
    return mode === 'edit' ? 'Elegí la fecha del gasto' : undefined
  }
  return DATE.test(date) ? undefined : 'Elegí una fecha válida'
}

/** La petición de alta. Sin categoría elegida, no la manda: así la resuelven las reglas. */
export function createRequest(expense: ValidExpense): CreateExpenseRequest {
  return {
    amount: expense.amount,
    merchant: expense.merchant,
    expenseDate: expense.expenseDate,
    paymentMethod: expense.paymentMethod,
    categoryId: expense.category === AUTO_CATEGORY || expense.category === UNCATEGORIZED ? undefined : expense.category,
  }
}

/**
 * La petición de edición, con solo lo que cambió: lo que no se manda queda como estaba. Así una
 * edición de otra persona en otro campo no se pisa con el valor viejo de este formulario, y un gasto
 * con una categoría dada de baja se puede corregir sin volver a mandarla.
 */
export function updateRequest(current: ExpenseResponse, expense: ValidExpense): UpdateExpenseRequest {
  const request: UpdateExpenseRequest = {}
  if (expense.amount !== current.amount) {
    request.amount = expense.amount
  }
  if (expense.merchant !== current.merchant) {
    request.merchant = expense.merchant
  }
  if (expense.expenseDate !== undefined && expense.expenseDate !== current.expenseDate) {
    request.expenseDate = expense.expenseDate
  }
  if (expense.paymentMethod !== current.paymentMethod) {
    request.paymentMethod = expense.paymentMethod
  }
  if (expense.category === UNCATEGORIZED) {
    if (current.categoryId !== null) {
      request.uncategorized = true
    }
  } else if (expense.category !== AUTO_CATEGORY && expense.category !== current.categoryId) {
    request.categoryId = expense.category
  }
  return request
}

export interface CategoryOption {
  value: string
  label: string
}

/**
 * Las categorías que ofrece el formulario. En el alta, la primera es dejar que decidan las reglas.
 * En la edición, la última es devolver el gasto a la bandeja, y si el gasto tiene una categoría que
 * se dio de baja, también aparece: el gasto la conserva (RN-16), y sin ella el formulario mostraría
 * otra que no es la suya.
 */
export function categoryOptions(
  categories: CategoryResponse[],
  mode: 'create' | 'edit',
  current?: ExpenseResponse,
): CategoryOption[] {
  const options = categories.map((c) => ({ value: c.id, label: c.name }))
  if (mode === 'create') {
    return [{ value: AUTO_CATEGORY, label: 'Automática, según el comercio' }, ...options]
  }
  if (current?.categoryId && !categories.some((c) => c.id === current.categoryId)) {
    options.push({ value: current.categoryId, label: `${current.categoryName ?? 'Categoría'} (dada de baja)` })
  }
  return [...options, { value: UNCATEGORIZED, label: 'Sin categoría (a la bandeja)' }]
}
