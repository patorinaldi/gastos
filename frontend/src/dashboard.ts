import type { AnalysisFilter } from './api/analysis.ts'
import type { IsoMonth, Money, MonthlyPoint, PaymentMethod, SummaryResponse } from './types/api.ts'
import { UNCATEGORIZED } from './types/api.ts'

/*
 * La lógica del tablero que no es dibujo: las porciones de la distribución, el filtro que deja un
 * clic en un gráfico (RF-37) y la serie de los últimos meses.
 *
 * Los importes se muestran siempre desde el texto que manda la API (RD-01). Solo para el tamaño de
 * una porción o de una barra, y para el porcentaje, se pasan a número: ahí un error de centavos no
 * cambia nada que se lea.
 */

/** La dimensión por la que se reparte el gasto en el donut (RF-35). */
export type Dimension = 'category' | 'owner' | 'paymentMethod'

export const DIMENSIONS: { value: Dimension; label: string }[] = [
  { value: 'category', label: 'Categoría' },
  { value: 'owner', label: 'Persona' },
  { value: 'paymentMethod', label: 'Medio de pago' },
]

/** Una porción elegida: filtra el resto de la pantalla a esa categoría, persona o medio de pago. */
export interface Focus {
  dimension: Dimension
  /** El identificador de la categoría (o `UNCATEGORIZED`) o de la persona, o el medio de pago. */
  key: string
}

export interface Slice {
  key: string
  label: string
  total: Money
  /** El total como número, solo para dibujar. */
  value: number
  color: string
  /** Porcentaje del total, de 0 a 100. */
  share: number
}

const PALETTE_SIZE = 8
const NEUTRAL = 'var(--color-chart-neutral)'
export const UNCATEGORIZED_LABEL = 'Sin categoría'

/**
 * Las porciones de una dimensión, de la más grande a la más chica, con su color. Los colores van en
 * el orden de la paleta (`--color-chart-1` a `--color-chart-8`). Lo que no tiene categoría va en
 * neutro y no consume un color de la paleta.
 */
export function slices(summary: SummaryResponse, dimension: Dimension): Slice[] {
  const raw = rawSlices(summary, dimension).sort((a, b) => Number(b.total) - Number(a.total))
  const sum = raw.reduce((acc, slice) => acc + Number(slice.total), 0)
  let colored = 0
  return raw.map((slice) => {
    const neutral = dimension === 'category' && slice.key === UNCATEGORIZED
    const color = neutral ? NEUTRAL : `var(--color-chart-${(colored++ % PALETTE_SIZE) + 1})`
    const value = Number(slice.total)
    return { ...slice, value, color, share: sum > 0 ? (value / sum) * 100 : 0 }
  })
}

function rawSlices(summary: SummaryResponse, dimension: Dimension): { key: string; label: string; total: Money }[] {
  switch (dimension) {
    case 'category':
      return summary.byCategory.map((c) => ({
        key: c.categoryId ?? UNCATEGORIZED,
        label: c.name ?? UNCATEGORIZED_LABEL,
        total: c.total,
      }))
    case 'owner':
      return summary.byOwner.map((o) => ({ key: o.ownerId, label: o.name, total: o.total }))
    case 'paymentMethod':
      return summary.byPaymentMethod.map((p) => ({ key: p.paymentMethod, label: p.paymentMethod, total: p.total }))
  }
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const PAYMENT_METHODS: readonly string[] = ['Efectivo', 'Tarjeta', 'Transferencia'] satisfies PaymentMethod[]

/**
 * La porción elegida, de la dirección. Usa los mismos nombres que los filtros de la API
 * (`categoryId`, `ownerId`, `paymentMethod`), así el enlace se lee igual que la consulta. Una sola a
 * la vez: si vienen varias, gana la primera en ese orden. Un valor inválido se ignora, en lugar de
 * mandarlo al servidor y mostrar un error.
 */
export function parseFocus(params: URLSearchParams): Focus | null {
  const category = params.get('categoryId')
  if (category !== null && (category === UNCATEGORIZED || UUID.test(category))) {
    return { dimension: 'category', key: category }
  }
  const owner = params.get('ownerId')
  if (owner !== null && UUID.test(owner)) {
    return { dimension: 'owner', key: owner }
  }
  const method = params.get('paymentMethod')
  if (method !== null && PAYMENT_METHODS.includes(method)) {
    return { dimension: 'paymentMethod', key: method }
  }
  return null
}

/**
 * La porción elegida como filtro de la API. Sirve también para la dirección: los nombres son los
 * mismos.
 */
export function focusFilter(focus: Focus | null): AnalysisFilter {
  if (!focus) {
    return {}
  }
  switch (focus.dimension) {
    case 'category':
      return { categoryId: focus.key }
    case 'owner':
      return { ownerId: focus.key }
    case 'paymentMethod':
      return { paymentMethod: focus.key as PaymentMethod }
  }
}

export interface MonthTotal {
  month: IsoMonth
  total: Money
  value: number
}

/**
 * La serie de los meses de la ventana, con cero en los que no tuvieron gastos: la API solo manda los
 * meses con gasto, y un hueco en las barras se leería como un mes que no existe. Los meses anteriores
 * al primer gasto no se muestran, porque todavía no había nada que contar (HU-14).
 */
export function monthSeries(points: MonthlyPoint[], window: IsoMonth[]): MonthTotal[] {
  const totals = new Map(points.map((p) => [p.month, p.total]))
  const first = window.findIndex((month) => totals.has(month))
  if (first < 0) {
    return []
  }
  return window.slice(first).map((month) => {
    const total = totals.get(month) ?? '0.00'
    return { month, total, value: Number(total) }
  })
}