import type { ExpenseFilter, MonthlySeriesResponse, SummaryResponse } from '../types/api.ts'
import { apiRequest, query } from './client.ts'

/**
 * M5, análisis. Piden sesión: el hogar es siempre el de quien consulta. Aceptan los mismos filtros
 * que el listado de gastos (RF-29), salvo la paginación.
 */
export type AnalysisFilter = Omit<ExpenseFilter, 'page' | 'size'>

/** El total del período y sus desgloses por categoría, integrante y medio de pago (RF-25, RF-26). */
export function getSummary(filter: AnalysisFilter) {
  return apiRequest<SummaryResponse>(`/api/expenses/summary${query(filter)}`)
}

/**
 * Un punto por mes con gasto y la media histórica (RF-27). La media no depende del período pedido:
 * se calcula sobre los meses anteriores al mes en curso, con los demás filtros.
 */
export function getMonthly(filter: AnalysisFilter) {
  return apiRequest<MonthlySeriesResponse>(`/api/expenses/monthly${query(filter)}`)
}