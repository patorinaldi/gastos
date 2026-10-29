import { describe, expect, it } from 'vitest'
import {
  currentMonth,
  includesMonth,
  monthFromParam,
  monthRange,
  monthsUntil,
  parseMonth,
  parsePeriod,
  periodParams,
  periodRange,
  shiftMonth,
} from './period.ts'

describe('currentMonth', () => {
  it('decide el mes en la hora de Argentina', () => {
    expect(currentMonth(new Date('2026-10-15T12:00:00Z'))).toBe('2026-10')
  })

  // A las 22 h del 31 en Argentina ya es el 1 en UTC: el mes sigue siendo el del 31.
  it('el 31 a la noche sigue siendo el mismo mes, aunque en UTC ya sea el siguiente', () => {
    expect(currentMonth(new Date('2026-11-01T01:00:00Z'))).toBe('2026-10')
    expect(currentMonth(new Date('2027-01-01T02:59:59Z'))).toBe('2026-12')
    expect(currentMonth(new Date('2027-01-01T03:00:00Z'))).toBe('2027-01')
  })
})

describe('parseMonth', () => {
  it('acepta un mes con año de cuatro dígitos y mes de dos', () => {
    expect(parseMonth('2026-10')).toBe('2026-10')
    expect(parseMonth('2026-01')).toBe('2026-01')
    expect(parseMonth('2026-12')).toBe('2026-12')
  })

  it('rechaza lo que no es un mes', () => {
    for (const value of [null, '', '2026-13', '2026-00', '2026-1', '26-10', '2026-10-01', 'octubre', ' 2026-10']) {
      expect(parseMonth(value), JSON.stringify(value)).toBeNull()
    }
  })
})

describe('monthFromParam', () => {
  it('usa el mes pedido si es válido y no es posterior al mes en curso', () => {
    expect(monthFromParam('2026-09', '2026-10')).toBe('2026-09')
    expect(monthFromParam('2025-12', '2026-10')).toBe('2025-12')
    expect(monthFromParam('2026-10', '2026-10')).toBe('2026-10')
  })

  // Un mes futuro no tiene gastos, y ninguna barra de la evolución quedaría marcada.
  it('un mes futuro abre el mes en curso', () => {
    expect(monthFromParam('2026-11', '2026-10')).toBe('2026-10')
    expect(monthFromParam('2030-01', '2026-10')).toBe('2026-10')
  })

  it('sin parámetro o con uno inválido abre el mes en curso', () => {
    expect(monthFromParam(null, '2026-10')).toBe('2026-10')
    expect(monthFromParam('2026-13', '2026-10')).toBe('2026-10')
  })
})

describe('shiftMonth', () => {
  it('avanza y retrocede, también cruzando el año', () => {
    expect(shiftMonth('2026-10', 1)).toBe('2026-11')
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2026-10', -12)).toBe('2025-10')
    expect(shiftMonth('2026-10', 0)).toBe('2026-10')
  })
})

describe('monthRange', () => {
  it('va del primer al último día del mes', () => {
    expect(monthRange('2026-10')).toEqual({ from: '2026-10-01', to: '2026-10-31' })
    expect(monthRange('2026-09')).toEqual({ from: '2026-09-01', to: '2026-09-30' })
  })

  it('febrero termina el 28, o el 29 en un año bisiesto', () => {
    expect(monthRange('2026-02')).toEqual({ from: '2026-02-01', to: '2026-02-28' })
    expect(monthRange('2028-02')).toEqual({ from: '2028-02-01', to: '2028-02-29' })
  })
})

describe('monthsUntil', () => {
  it('da los meses que terminan en el indicado, del más viejo al más nuevo', () => {
    expect(monthsUntil('2026-02', 4)).toEqual(['2025-11', '2025-12', '2026-01', '2026-02'])
    expect(monthsUntil('2026-10', 12)).toHaveLength(12)
    expect(monthsUntil('2026-10', 12)[0]).toBe('2025-11')
  })
})

describe('parsePeriod', () => {
  const CURRENT = '2026-10'
  const params = (query: string) => new URLSearchParams(query)

  it('lee un mes o un año de la dirección', () => {
    expect(parsePeriod(params('month=2026-07'), CURRENT)).toEqual({ kind: 'month', month: '2026-07' })
    expect(parsePeriod(params('year=2026'), CURRENT)).toEqual({ kind: 'year', year: 2026 })
  })

  it('sin período, o con uno inválido o futuro, es el mes en curso', () => {
    for (const query of ['', 'month=2026-13', 'year=26', 'year=dos mil', 'month=2026-11', 'year=2027']) {
      expect(parsePeriod(params(query), CURRENT), query).toEqual({ kind: 'month', month: CURRENT })
    }
  })

  it('vuelve a la dirección como la leyó, sin escribir el mes en curso', () => {
    expect(periodParams({ kind: 'month', month: '2026-07' }, CURRENT)).toEqual({ month: '2026-07' })
    expect(periodParams({ kind: 'month', month: CURRENT }, CURRENT)).toEqual({})
    expect(periodParams({ kind: 'year', year: 2026 }, CURRENT)).toEqual({ year: '2026' })
  })
})

describe('periodRange', () => {
  it('un año va del 1 de enero al 31 de diciembre', () => {
    expect(periodRange({ kind: 'year', year: 2026 })).toEqual({ from: '2026-01-01', to: '2026-12-31' })
    expect(periodRange({ kind: 'month', month: '2026-02' })).toEqual({ from: '2026-02-01', to: '2026-02-28' })
  })
})

describe('includesMonth', () => {
  it('un mes incluye solo ese mes; un año, sus doce meses', () => {
    expect(includesMonth({ kind: 'month', month: '2026-07' }, '2026-07')).toBe(true)
    expect(includesMonth({ kind: 'month', month: '2026-07' }, '2026-08')).toBe(false)
    expect(includesMonth({ kind: 'year', year: 2026 }, '2026-01')).toBe(true)
    expect(includesMonth({ kind: 'year', year: 2026 }, '2025-12')).toBe(false)
  })
})