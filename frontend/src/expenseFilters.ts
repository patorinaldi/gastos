import { isUuid } from './dashboard.ts'
import { PAYMENT_METHODS } from './expenseForm.ts'
import { parsePeriod, periodParams, periodRange, type Period } from './period.ts'
import { UNCATEGORIZED, type ExpenseFilter, type IsoMonth, type PaymentMethod } from './types/api.ts'

/*
 * Los filtros del listado de movimientos (RF-18, HU-10), que viven en la dirección:
 * `/expenses?month=2026-09&categoryId=...&paymentMethod=Tarjeta&page=2`.
 *
 * Usan los mismos nombres que la API y que el tablero, así "Ver todos" en el tablero abre los mismos
 * movimientos, y el enlace se lee igual que la consulta. A diferencia del tablero, que enfoca una
 * sola porción, acá los filtros se combinan.
 */

/** La última página que acepta la API (ExpenseContracts.MAX_PAGE). */
const MAX_PAGE = 10_000

export interface ExpenseFilters {
  period: Period
  /** El identificador de una categoría, o `UNCATEGORIZED`. */
  categoryId?: string
  ownerId?: string
  paymentMethod?: PaymentMethod
  /** Desde 0, como en la API. */
  page: number
}

/**
 * Los filtros de la dirección. La escribe cualquiera: un valor inválido se ignora, en lugar de
 * mandarlo al servidor y mostrar un error.
 */
export function parseFilters(params: URLSearchParams, current: IsoMonth): ExpenseFilters {
  const category = params.get('categoryId')
  const owner = params.get('ownerId')
  const method = params.get('paymentMethod')
  const page = Number(params.get('page'))
  return {
    period: parsePeriod(params, current),
    categoryId: category !== null && (category === UNCATEGORIZED || isUuid(category)) ? category : undefined,
    ownerId: owner !== null && isUuid(owner) ? owner : undefined,
    paymentMethod: PAYMENT_METHODS.find((m) => m === method),
    page: Number.isInteger(page) && page > 0 && page <= MAX_PAGE ? page : 0,
  }
}

/** Los filtros como parámetros de la dirección. Lo que tiene el valor por defecto no se escribe. */
export function filterParams(filters: ExpenseFilters, current: IsoMonth): Record<string, string> {
  const params: Record<string, string> = periodParams(filters.period, current)
  if (filters.categoryId) {
    params.categoryId = filters.categoryId
  }
  if (filters.ownerId) {
    params.ownerId = filters.ownerId
  }
  if (filters.paymentMethod) {
    params.paymentMethod = filters.paymentMethod
  }
  if (filters.page > 0) {
    params.page = String(filters.page)
  }
  return params
}

/** Los filtros como consulta a la API. */
export function filterQuery(filters: ExpenseFilters, size: number): ExpenseFilter {
  return {
    ...periodRange(filters.period),
    categoryId: filters.categoryId,
    ownerId: filters.ownerId,
    paymentMethod: filters.paymentMethod,
    page: filters.page,
    size,
  }
}

/** Si hay algún filtro además del período. */
export function hasFilters(filters: ExpenseFilters): boolean {
  return Boolean(filters.categoryId || filters.ownerId || filters.paymentMethod)
}
