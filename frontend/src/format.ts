import type { IsoDate, IsoMonth, Money } from './types/api.ts'

/*
 * Formato de importes y fechas para Argentina (es-AR). Toda pantalla que muestra un importe o una
 * fecha pasa por acá, así se ven igual en todos lados.
 */

const money = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })

/**
 * "1234.56" → "$ 1.234,56".
 *
 * Recibe el importe como texto, tal como llega de la API, y se lo pasa así a Intl, que formatea
 * cadenas decimales sin convertirlas a número: el importe no pasa por el punto flotante de
 * JavaScript ni para mostrarse (RD-01).
 */
export function formatMoney(amount: Money): string {
  return money.format(amount as Intl.StringNumericLiteral)
}

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
const MONTHS_LONG = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
]

/*
 * Las fechas de un gasto no tienen hora. new Date('2026-09-21') las interpretaría como medianoche
 * UTC, que en Argentina todavía es el 20: por eso se separan a mano y nunca pasan por Date.
 */
function parts(date: IsoDate | IsoMonth): { year: number; month: number; day: number } {
  const [year, month, day = 1] = date.split('-').map(Number)
  return { year, month, day }
}

/** "2026-09-21" → "21 sep". */
export function formatDayMonth(date: IsoDate): string {
  const { month, day } = parts(date)
  return `${day} ${MONTHS[month - 1]}`
}

/** "2026-09-21" → "21/09/2026". */
export function formatDate(date: IsoDate): string {
  const { year, month, day } = parts(date)
  return `${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}/${year}`
}

/** "2026-09" → "septiembre 2026". */
export function formatMonth(month: IsoMonth): string {
  const { year, month: m } = parts(month)
  return `${MONTHS_LONG[m - 1]} ${year}`
}