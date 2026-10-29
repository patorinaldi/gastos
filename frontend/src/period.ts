import type { IsoDate, IsoMonth } from './types/api.ts'

/*
 * El período del tablero: un mes calendario, que es el período por defecto del análisis (RD-07).
 *
 * El mes en curso se decide en la hora de Argentina (RD-08), la misma zona con la que el backend
 * completa la fecha de un gasto y elige el mes por defecto. Con la zona del dispositivo, en un teléfono configurado
 * en UTC el 31 de octubre a las 22 h de Argentina ya sería noviembre, y el tablero abriría en un mes
 * distinto del que el servidor considera en curso.
 *
 * Los meses se manejan como texto "2026-10" y nunca pasan por Date para compararse: con ese formato,
 * el orden alfabético es el cronológico.
 */

const ZONE = 'America/Argentina/Buenos_Aires'

const MONTH = /^(\d{4})-(0[1-9]|1[0-2])$/

// en-CA da los números con dos dígitos y sin texto, que es lo que hace falta para armar "2026-10".
const zoneParts = new Intl.DateTimeFormat('en-CA', { timeZone: ZONE, year: 'numeric', month: '2-digit' })

/** El mes en curso en la hora de Argentina: "2026-10". */
export function currentMonth(now: Date = new Date()): IsoMonth {
  const parts = zoneParts.formatToParts(now)
  const year = parts.find((part) => part.type === 'year')?.value
  const month = parts.find((part) => part.type === 'month')?.value
  return `${year}-${month}`
}

/**
 * El mes de un parámetro de la dirección, o null si no es un mes válido. La dirección la puede
 * escribir cualquiera, así que se valida antes de pedirle nada al servidor.
 */
export function parseMonth(value: string | null): IsoMonth | null {
  return value !== null && MONTH.test(value) ? value : null
}

/**
 * El mes que muestra el tablero para el parámetro de la dirección: el pedido, si es válido y no
 * es posterior a `current`; si no, `current`. Un mes futuro no tiene gastos, y la evolución no
 * llega hasta él: ninguna barra quedaría marcada como el mes elegido.
 */
export function monthFromParam(value: string | null, current: IsoMonth): IsoMonth {
  const requested = parseMonth(value)
  return requested !== null && requested <= current ? requested : current
}

/** El mes que está `delta` meses antes (negativo) o después (positivo): "2026-01", -1 → "2025-12". */
export function shiftMonth(month: IsoMonth, delta: number): IsoMonth {
  const [year, m] = month.split('-').map(Number)
  const index = year * 12 + (m - 1) + delta
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`
}

/** Primer y último día del mes, inclusive, como los pide el análisis: "2026-02" → 01 y 28. */
export function monthRange(month: IsoMonth): { from: IsoDate; to: IsoDate } {
  const [year, m] = month.split('-').map(Number)
  // El día 0 del mes siguiente es el último de este. Date.UTC evita que la zona corra el día.
  const lastDay = new Date(Date.UTC(year, m, 0)).getUTCDate()
  return { from: `${month}-01`, to: `${month}-${String(lastDay).padStart(2, '0')}` }
}

/** Los `count` meses que terminan en `last`, del más viejo al más nuevo. */
export function monthsUntil(last: IsoMonth, count: number): IsoMonth[] {
  return Array.from({ length: count }, (_, i) => shiftMonth(last, i - count + 1))
}

/**
 * El período que se mira en el tablero: un mes, o un año entero. El mes es el caso común (RD-07); el
 * año sirve para ver la distribución acumulada.
 */
export type Period = { kind: 'month'; month: IsoMonth } | { kind: 'year'; year: number }

const YEAR = /^\d{4}$/

/**
 * El período de la dirección: `?month=2026-09` o `?year=2026`. Sin ninguno, con uno inválido o con
 * uno futuro, el mes en curso.
 */
export function parsePeriod(params: URLSearchParams, current: IsoMonth): Period {
  if (parseMonth(params.get('month'))) {
    return { kind: 'month', month: monthFromParam(params.get('month'), current) }
  }
  const year = params.get('year')
  // Un año futuro, como un mes futuro, no tiene gastos: se abre el mes en curso.
  if (year !== null && YEAR.test(year) && Number(year) <= Number(current.slice(0, 4))) {
    return { kind: 'year', year: Number(year) }
  }
  return { kind: 'month', month: current }
}

/** El período como parámetros de la dirección. El mes en curso es el valor por defecto y no se escribe. */
export function periodParams(period: Period, current: IsoMonth): Record<string, string> {
  if (period.kind === 'year') {
    return { year: String(period.year) }
  }
  return period.month === current ? {} : { month: period.month }
}

/** Primer y último día del período, inclusive. */
export function periodRange(period: Period): { from: IsoDate; to: IsoDate } {
  if (period.kind === 'year') {
    return { from: `${period.year}-01-01`, to: `${period.year}-12-31` }
  }
  return monthRange(period.month)
}

/** Si el mes cae dentro del período. */
export function includesMonth(period: Period, month: IsoMonth): boolean {
  return period.kind === 'month' ? period.month === month : month.startsWith(`${period.year}-`)
}