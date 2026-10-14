import { describe, expect, it } from 'vitest'
import { formatDate, formatDayMonth, formatMoney, formatMonth } from './format.ts'

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

describe('formatDayMonth', () => {
  it('formatea una fecha como día y mes abreviado', () => {
    expect(formatDayMonth('2026-09-21')).toBe('21 sep')
    expect(formatDayMonth('2026-01-05')).toBe('5 ene')
  })

  it('devuelve tal cual lo que no es una fecha', () => {
    expect(formatDayMonth('2026-09')).toBe('2026-09')
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