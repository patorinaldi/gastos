import { describe, expect, it } from 'vitest'
import { filterParams, filterQuery, hasFilters, parseFilters } from './expenseFilters.ts'

const CURRENT = '2026-10'
const SUPER = '11111111-1111-1111-1111-111111111111'
const ANA = '44444444-4444-4444-4444-444444444444'

const parse = (search: string) => parseFilters(new URLSearchParams(search), CURRENT)

describe('parseFilters', () => {
  it('sin parámetros, el mes en curso, sin filtros y en la primera página', () => {
    expect(parse('')).toEqual({ period: { kind: 'month', month: CURRENT }, page: 0 })
  })

  it('combina los filtros', () => {
    expect(parse(`month=2026-09&categoryId=${SUPER}&ownerId=${ANA}&paymentMethod=Tarjeta&page=2`)).toEqual({
      period: { kind: 'month', month: '2026-09' },
      categoryId: SUPER,
      ownerId: ANA,
      paymentMethod: 'Tarjeta',
      page: 2,
    })
  })

  it('acepta la bandeja como categoría', () => {
    expect(parse('categoryId=uncategorized').categoryId).toBe('uncategorized')
  })

  it('ignora los valores inválidos en lugar de mandarlos al servidor', () => {
    expect(parse('categoryId=otra&ownerId=1&paymentMethod=Cheque&page=-1')).toEqual({
      period: { kind: 'month', month: CURRENT },
      page: 0,
    })
    expect(parse('page=1.5').page).toBe(0)
    expect(parse('page=10001').page).toBe(0)
  })
})

describe('filterParams', () => {
  it('no escribe lo que tiene el valor por defecto', () => {
    expect(filterParams(parse(''), CURRENT)).toEqual({})
  })

  it('vuelve a dar los mismos filtros al leerlo', () => {
    const filters = parse(`year=2026&paymentMethod=Efectivo&page=3`)
    expect(parse(new URLSearchParams(filterParams(filters, CURRENT)).toString())).toEqual(filters)
  })
})

describe('filterQuery', () => {
  it('pide el período completo, con los filtros y la página', () => {
    expect(filterQuery(parse(`month=2026-02&ownerId=${ANA}&page=1`), 20)).toEqual({
      from: '2026-02-01',
      to: '2026-02-28',
      categoryId: undefined,
      ownerId: ANA,
      paymentMethod: undefined,
      page: 1,
      size: 20,
    })
  })
})

describe('hasFilters', () => {
  it('el período y la página no cuentan como filtros', () => {
    expect(hasFilters(parse('month=2026-09&page=2'))).toBe(false)
    expect(hasFilters(parse('paymentMethod=Tarjeta'))).toBe(true)
  })
})
