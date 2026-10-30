import type {
  CreateExpenseRequest,
  ExpenseFilter,
  ExpenseResponse,
  PageResponse,
  UpdateExpenseRequest,
  Uuid,
} from '../types/api.ts'
import { apiRequest, query } from './client.ts'

/** M3, gastos. Piden sesión: el hogar es siempre el de quien consulta. */

/** Los gastos que cumplen los filtros, por fecha descendente y de a una página (RF-17, RF-18). */
export function listExpenses(filter: ExpenseFilter) {
  return apiRequest<PageResponse<ExpenseResponse>>(`/api/expenses${query(filter)}`)
}

/** Un gasto de otro hogar responde 404, igual que uno inexistente. */
export function getExpense(id: Uuid) {
  return apiRequest<ExpenseResponse>(`/api/expenses/${encodeURIComponent(id)}`)
}

/** RF-13. Sin categoría, la resuelven las reglas del hogar; si ninguna coincide, va a la bandeja. */
export function createExpense(request: CreateExpenseRequest) {
  return apiRequest<ExpenseResponse>('/api/expenses', { method: 'POST', body: request })
}

/** RF-15. Lo que no viene en la petición no cambia. */
export function updateExpense(id: Uuid, request: UpdateExpenseRequest) {
  return apiRequest<ExpenseResponse>(`/api/expenses/${encodeURIComponent(id)}`, { method: 'PATCH', body: request })
}

/** RF-16. Baja lógica: deja de contar en el listado y el análisis, pero se conserva. */
export function deleteExpense(id: Uuid) {
  return apiRequest<void>(`/api/expenses/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
