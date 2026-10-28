import type { ExpenseFilter, SummaryResponse } from '../types/api.ts'
import { apiRequest } from './client.ts'

/**
 * M5, análisis. Piden sesión: el hogar es siempre el de quien consulta. Aceptan los mismos filtros
 * que el listado de gastos (RF-29), salvo la paginación.
 */
export type AnalysisFilter = Omit<ExpenseFilter, 'page' | 'size'>

/** El total del período y sus desgloses por categoría, integrante y medio de pago (RF-25, RF-26). */
export function getSummary(filter: AnalysisFilter) {
  return apiRequest<SummaryResponse>(`/api/expenses/summary${query(filter)}`)
}

/** Los filtros como parámetros de consulta. Los que no vienen no se mandan: sin filtro es sin restricción. */
export function query(filter: AnalysisFilter): string {
  const params = new URLSearchParams()
  for (const [name, value] of Object.entries(filter)) {
    if (value !== undefined && value !== '') {
      params.set(name, value)
    }
  }
  const text = params.toString()
  return text === '' ? '' : `?${text}`
}