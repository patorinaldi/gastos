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