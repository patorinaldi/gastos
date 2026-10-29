import { describe, expect, it } from 'vitest'
import {
  formatDate,
  formatDayMonth,
  formatInstant,
  formatMoney,
  formatMoneyCompact,
  formatMoneyParts,
  formatMonth,
  formatMonthShort,
} from './format.ts'

// Intl separa el símbolo de la moneda con un espacio duro (U+00A0), no con uno común.
const NBSP = ' '

describe('formatMoney', () => {
  it('formatea un importe con separador de miles y dos decimales', () => {
    expect(formatMoney('1234.56')).toBe(`$${NBSP}1.234,56`)
  })

  it('conserva el cero final de los decimales', () => {
    expect(formatMoney('1234.50')).toBe(`$${NBSP}1.234,50`)
  })

  it('no pierde centavos en importes que el punto flotante no representa exacto', () => {
    expect(formatMoney('9999999999.99')).toBe(`$${NBSP}9.999.999.999,99`)
    expect(formatMoney('0.10')).toBe(`$${NBSP}0,10`)
  })

  it('devuelve tal cual un texto que no es un importe', () => {
    expect(formatMoney('abc')).toBe('abc')
    expect(formatMoney('')).toBe('')
    expect(formatMoney('1.234,56')).toBe('1.234,56')
  })
})

describe('formatDate', () => {
  it('formatea una fecha como día/mes/año', () => {
    expect(formatDate('2026-09-21')).toBe('21/09/2026')
  })

  // new Date('2026-09-01') la tomaría como medianoche UTC, que en Argentina es el 31 de agosto.
  it('no corre la fecha un día por la zona horaria', () => {
    expect(formatDate('2026-09-01')).toBe('01/09/2026')
  })

  it('devuelve tal cual lo que no es una fecha sin hora', () => {
    expect(formatDate('2026-09-21T13:45:00Z')).toBe('2026-09-21T13:45:00Z')
    expect(formatDate('')).toBe('')
    expect(formatDate('21/09/2026')).toBe('21/09/2026')
  })

  it('devuelve tal cual una fecha que no existe', () => {
    expect(formatDate('2026-02-31')).toBe('2026-02-31')
    expect(formatDate('2026-13-01')).toBe('2026-13-01')
  })
})

describe('formatInstant', () => {
  it('formatea un instante con fecha y hora de Argentina', () => {
    expect(formatInstant('2026-11-05T17:30:00Z')).toBe('05/11/2026 14:30')
  })

  // A las 2 de la mañana UTC todavía es el día anterior en Argentina (RD-08).
  it('toma el día de Argentina, no el de UTC', () => {
    expect(formatInstant('2026-11-06T02:15:00Z')).toBe('05/11/2026 23:15')
  })

  it('devuelve tal cual lo que no es un instante', () => {
    expect(formatInstant('2026-11-05')).toBe('2026-11-05')
    expect(formatInstant('')).toBe('')
    expect(formatInstant('mañana')).toBe('mañana')
  })
})

describe('formatDayMonth', () => {
  it('formatea una fecha como día y mes abreviado', () => {
    expect(formatDayMonth('2026-09-21')).toBe('21 sep')
    expect(formatDayMonth('2026-01-05')).toBe('5 ene')
  })

  it('devuelve tal cual lo que no es una fecha', () => {
    expect(formatDayMonth('2026-09')).toBe('2026-09')
  })
})

describe('formatMoneyParts', () => {
  it('separa el símbolo de la cifra, con el mismo formato que formatMoney', () => {
    expect(formatMoneyParts('1234.56')).toEqual({ currency: '$', number: '1.234,56' })
    expect(formatMoneyParts('0.00')).toEqual({ currency: '$', number: '0,00' })
  })

  it('devuelve tal cual lo que no es un importe', () => {
    expect(formatMoneyParts('abc')).toEqual({ currency: '', number: 'abc' })
  })
})

// Intl separa el símbolo con un espacio duro; se comparan sin espacios.
const noSpaces = (text: string) => text.replace(/\s/g, '')

describe('formatMoneyCompact', () => {
  // El sufijo es fijo: con notation: 'compact', Node 20 da "K" y Node 22 da "k".
  it('abrevia los miles, los millones y los miles de millones con un decimal', () => {
    expect(formatMoneyCompact('74070.75')).toBe(`$${NBSP}74,1${NBSP}K`)
    expect(formatMoneyCompact('1234567.89')).toBe(`$${NBSP}1,2${NBSP}M`)
    expect(formatMoneyCompact('2500000000.00')).toBe(`$${NBSP}2,5${NBSP}mil${NBSP}M`)
  })

  it('sin decimal cuando es redondo', () => {
    expect(formatMoneyCompact('5000.00')).toBe(`$${NBSP}5${NBSP}K`)
  })

  it('pasa a la escala siguiente cuando el redondeo llega a mil', () => {
    expect(formatMoneyCompact('999.50')).toBe(`$${NBSP}1${NBSP}K`)
    expect(formatMoneyCompact('999950.00')).toBe(`$${NBSP}1${NBSP}M`)
    expect(formatMoneyCompact('999999999.99')).toBe(`$${NBSP}1${NBSP}mil${NBSP}M`)
  })

  it('debajo de mil, el importe redondeado sin decimales', () => {
    expect(noSpaces(formatMoneyCompact('950.50'))).toBe('$951')
    expect(noSpaces(formatMoneyCompact('999.49'))).toBe('$999')
    expect(noSpaces(formatMoneyCompact('0.00'))).toBe('$0')
  })

  it('devuelve tal cual lo que no es un importe', () => {
    expect(formatMoneyCompact('abc')).toBe('abc')
  })
})

describe('formatMonthShort', () => {
  it('formatea un mes con su abreviatura', () => {
    expect(formatMonthShort('2026-09')).toBe('sep')
    expect(formatMonthShort('2027-01')).toBe('ene')
  })

  it('devuelve tal cual lo que no es un mes', () => {
    expect(formatMonthShort('2026-13')).toBe('2026-13')
  })
})

describe('formatMonth', () => {
  it('formatea un mes con su nombre completo', () => {
    expect(formatMonth('2026-09')).toBe('septiembre 2026')
    expect(formatMonth('2026-12')).toBe('diciembre 2026')
  })

  it('devuelve tal cual lo que no es un mes', () => {
    expect(formatMonth('')).toBe('')
    expect(formatMonth('2026-00')).toBe('2026-00')
    expect(formatMonth('2026-09-21')).toBe('2026-09-21')
  })
})