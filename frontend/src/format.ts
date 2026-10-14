import type { IsoDate, IsoMonth, Money } from './types/api.ts'

/*
 * Formato de importes y fechas para Argentina (es-AR). Toda pantalla que muestra un importe o una
 * fecha pasa por acá, así se ven igual en todos lados.
 *
 * Los tipos IsoDate, IsoMonth y Money son alias de string: el compilador no impide pasar un
 * instante donde va una fecha, o un texto cualquiera donde va un importe. Por eso cada función
 * valida lo que recibe, y si no tiene el formato esperado devuelve el texto tal cual llegó. La
 * pantalla muestra algo reconocible ("2026-09-21T13:45:00Z") en lugar de "NaN/09/2026", y el error
 * queda a la vista sin romper el renderizado.
 */

const MONEY = /^-?\d+(\.\d+)?$/
const DATE = /^(\d{4})-(\d{2})-(\d{2})$/
const MONTH = /^(\d{4})-(\d{2})$/

const money = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })

/**
 * "1234.56" → "$ 1.234,56".
 *
 * Recibe el importe como texto, tal como llega de la API, y se lo pasa así a Intl, que formatea
 * cadenas decimales sin convertirlas a número: el importe no pasa por el punto flotante de
 * JavaScript ni para mostrarse (RD-01).
 */
export function formatMoney(amount: Money): string {
  if (!MONEY.test(amount)) {
    return amount
  }
  return money.format(amount as Intl.StringNumericLiteral)
}

const MONTHS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
const MONTHS_LONG = [
  'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
  'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
]

/*
 * Las fechas de un gasto no tienen hora. new Date('2026-09-21') las interpretaría como medianoche
 * UTC, que en Argentina todavía es el 20: por eso se separan a mano y nunca pasan por Date para
 * mostrarse. Date.UTC solo se usa para comprobar que el día exista en ese mes (el 31/02 no).
 */
function dateParts(date: string): { year: number; month: number; day: number } | null {
  const match = DATE.exec(date)
  if (!match) {
    return null
  }
  const [year, month, day] = match.slice(1).map(Number)
  const check = new Date(Date.UTC(year, month - 1, day))
  const exists = check.getUTCFullYear() === year && check.getUTCMonth() === month - 1 && check.getUTCDate() === day
  return exists ? { year, month, day } : null
}

function monthParts(month: string): { year: number; month: number } | null {
  const match = MONTH.exec(month)
  if (!match) {
    return null
  }
  const [year, m] = match.slice(1).map(Number)
  return m >= 1 && m <= 12 ? { year, month: m } : null
}

/** "2026-09-21" → "21 sep". */
export function formatDayMonth(date: IsoDate): string {
  const parts = dateParts(date)
  return parts ? `${parts.day} ${MONTHS[parts.month - 1]}` : date
}

/** "2026-09-21" → "21/09/2026". */
export function formatDate(date: IsoDate): string {
  const parts = dateParts(date)
  if (!parts) {
    return date
  }
  return `${String(parts.day).padStart(2, '0')}/${String(parts.month).padStart(2, '0')}/${parts.year}`
}

/** "2026-09" → "septiembre 2026". */
export function formatMonth(month: IsoMonth): string {
  const parts = monthParts(month)
  return parts ? `${MONTHS_LONG[parts.month - 1]} ${parts.year}` : month
}
