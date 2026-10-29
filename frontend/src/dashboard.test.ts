import { describe, expect, it } from 'vitest'
import { focusFilter, monthSeries, parseFocus, slices } from './dashboard.ts'
import type { SummaryResponse } from './types/api.ts'

const SUPER = '11111111-1111-1111-1111-111111111111'
const NAFTA = '22222222-2222-2222-2222-222222222222'
const ANA = '33333333-3333-3333-3333-333333333333'

const summary: SummaryResponse = {
  total: '1000.00',
  byCategory: [
    { categoryId: NAFTA, name: 'Nafta', total: '200.00' },
    { categoryId: null, name: null, total: '300.00' },
    { categoryId: SUPER, name: 'Supermercado', total: '500.00' },
  ],
  byOwner: [{ ownerId: ANA, name: 'Ana', total: '1000.00' }],
  byPaymentMethod: [
    { paymentMethod: 'Efectivo', total: '250.00' },
    { paymentMethod: 'Tarjeta', total: '750.00' },
  ],
}

describe('slices', () => {
  it('ordena de la más grande a la más chica, con su porcentaje', () => {
    const result = slices(summary, 'category')
    expect(result.map((s) => s.label)).toEqual(['Supermercado', 'Sin categoría', 'Nafta'])
    expect(result.map((s) => s.share)).toEqual([50, 30, 20])
    expect(result[0].total).toBe('500.00')
  })

  // Lo sin categoría va en neutro y no consume un color: Nafta queda con el segundo de la paleta.
  it('lo que no tiene categoría va en neutro, y las categorías toman la paleta en orden', () => {
    const result = slices(summary, 'category')
    expect(result.map((s) => s.color)).toEqual([
      'var(--color-chart-1)',
      'var(--color-chart-neutral)',
      'var(--color-chart-2)',
    ])
    expect(result[1].key).toBe('uncategorized')
  })

  it('reparte también por persona y por medio de pago', () => {
    expect(slices(summary, 'owner')).toMatchObject([{ key: ANA, label: 'Ana', share: 100 }])
    expect(slices(summary, 'paymentMethod').map((s) => s.key)).toEqual(['Tarjeta', 'Efectivo'])
  })

  const categories = (count: number): SummaryResponse['byCategory'] =>
    Array.from({ length: count }, (_, i) => ({
      categoryId: `00000000-0000-0000-0000-0000000000${String(i).padStart(2, '0')}`,
      name: `C${i}`,
      total: `${100 - i}.00`,
    }))
  const colors = (byCategory: SummaryResponse['byCategory']) =>
    slices({ ...summary, byCategory }, 'category').map((s) => s.color.replace(/var\(--color-chart-(.+)\)/, '$1'))

  it('con más porciones que colores, la paleta vuelve a empezar', () => {
    expect(colors(categories(10))).toEqual(['1', '2', '3', '4', '5', '6', '7', '8', '1', '2'])
  })

  // El anillo se cierra: con 9, la última tocaría a la primera con el mismo color.
  it('la última no repite el color de la primera, que la toca al cerrar el anillo', () => {
    expect(colors(categories(9))).toEqual(['1', '2', '3', '4', '5', '6', '7', '8', '2'])
    expect(colors(categories(17)).slice(-2)).toEqual(['8', '2'])
  })

  it('el cierre del anillo cuenta lo sin categoría como vecina', () => {
    const byCategory = [...categories(9)]
    byCategory.splice(8, 0, { categoryId: null, name: null, total: '92.50' })
    expect(colors(byCategory).slice(-2)).toEqual(['neutral', '2'])
    const first = [{ categoryId: null, name: null, total: '500.00' }, ...categories(9)]
    expect(colors(first)).toEqual(['neutral', '1', '2', '3', '4', '5', '6', '7', '8', '1'])
  })

  it('sin gastos no hay porciones', () => {
    const empty: SummaryResponse = { total: '0.00', byCategory: [], byOwner: [], byPaymentMethod: [] }
    expect(slices(empty, 'category')).toEqual([])
  })
})

describe('parseFocus', () => {
  const params = (query: string) => new URLSearchParams(query)

  it('lee la porción elegida con los nombres de los filtros de la API', () => {
    expect(parseFocus(params(`categoryId=${SUPER}`))).toEqual({ dimension: 'category', key: SUPER })
    expect(parseFocus(params('categoryId=uncategorized'))).toEqual({ dimension: 'category', key: 'uncategorized' })
    expect(parseFocus(params(`ownerId=${ANA}`))).toEqual({ dimension: 'owner', key: ANA })
    expect(parseFocus(params('paymentMethod=Tarjeta'))).toEqual({ dimension: 'paymentMethod', key: 'Tarjeta' })
  })

  it('ignora lo que no es un filtro válido', () => {
    for (const query of ['', 'categoryId=super', 'ownerId=ana', 'paymentMethod=Cheque', 'paymentMethod=tarjeta']) {
      expect(parseFocus(params(query)), query).toBeNull()
    }
  })

  it('una sola porción a la vez', () => {
    expect(parseFocus(params(`paymentMethod=Tarjeta&categoryId=${SUPER}`))).toEqual({ dimension: 'category', key: SUPER })
  })
})

describe('focusFilter', () => {
  it('convierte la porción en el filtro de la API', () => {
    expect(focusFilter(null)).toEqual({})
    expect(focusFilter({ dimension: 'category', key: SUPER })).toEqual({ categoryId: SUPER })
    expect(focusFilter({ dimension: 'owner', key: ANA })).toEqual({ ownerId: ANA })
    expect(focusFilter({ dimension: 'paymentMethod', key: 'Efectivo' })).toEqual({ paymentMethod: 'Efectivo' })
  })
})

describe('monthSeries', () => {
  const window = ['2026-06', '2026-07', '2026-08', '2026-09', '2026-10']

  it('completa con cero los meses sin gasto y arranca en el primero con gasto', () => {
    const result = monthSeries(
      [
        { month: '2026-07', total: '100.00' },
        { month: '2026-09', total: '250.50' },
      ],
      window,
    )
    expect(result.map((m) => [m.month, m.total])).toEqual([
      ['2026-07', '100.00'],
      ['2026-08', '0.00'],
      ['2026-09', '250.50'],
      ['2026-10', '0.00'],
    ])
    expect(result[2].value).toBe(250.5)
  })

  it('sin gastos en la ventana no hay serie', () => {
    expect(monthSeries([], window)).toEqual([])
    expect(monthSeries([{ month: '2025-01', total: '5.00' }], window)).toEqual([])
  })
})