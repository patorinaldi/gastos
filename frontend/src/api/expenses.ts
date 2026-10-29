import type { ExpenseFilter, ExpenseResponse, PageResponse } from '../types/api.ts'
import { apiRequest, query } from './client.ts'

/** M3, gastos. Piden sesión: el hogar es siempre el de quien consulta. */

/** Los gastos que cumplen los filtros, por fecha descendente y de a una página (RF-17, RF-18). */
export function listExpenses(filter: ExpenseFilter) {
  return apiRequest<PageResponse<ExpenseResponse>>(`/api/expenses${query(filter)}`)
}