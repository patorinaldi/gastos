import { describe, expect, it } from 'vitest'
import { parseAmount, toAmountInput } from './amount.ts'

/** El importe, o falla la prueba con el error que dio. */
function amount(text: string): string {
  const result = parseAmount(text)
  if (!result.ok) {
    throw new Error(`"${text}" no se aceptó: ${result.error}`)
  }
  return result.amount
}

function error(text: string): string {
  const result = parseAmount(text)
  if (result.ok) {
    throw new Error(`"${text}" se aceptó como ${result.amount}`)
  }
  return result.error
}

// Los mismos casos que AmountParserTest del backend: un importe se lee igual en los dos lados.
describe('parseAmount', () => {
  it('acepta un entero sin separadores', () => {
    expect(amount('1234')).toBe('1234.00')
  })

  it('acepta el formato local y el anglosajón con los dos separadores', () => {
    expect(amount('1.234,56')).toBe('1234.56')
    expect(amount('1,234.56')).toBe('1234.56')
    expect(amount('1.234.567,89')).toBe('1234567.89')
  })

  it('con un solo separador y uno o dos dígitos detrás, lo lee como decimal', () => {
    expect(amount('12,5')).toBe('12.50')
    expect(amount('12.50')).toBe('12.50')
    expect(amount('0,99')).toBe('0.99')
  })

  it('con un solo separador y tres dígitos detrás, lo lee como de miles', () => {
    expect(amount('1.234')).toBe('1234.00')
    expect(amount('1,234')).toBe('1234.00')
  })

  it('un separador repetido es de miles', () => {
    expect(amount('1.234.567')).toBe('1234567.00')
  })

  it('ignora el signo de la moneda y los espacios de los bordes, también el duro', () => {
    expect(amount('$ 1.234,56')).toBe('1234.56')
    expect(amount('ARS 1234')).toBe('1234.00')
    expect(amount('1234 ARS')).toBe('1234.00')
    expect(amount('\u00a01234,5\u00a0')).toBe('1234.50')
  })

  it('quita los ceros de la izquierda', () => {
    expect(amount('0012')).toBe('12.00')
  })

  it('pide el importe si no hay nada', () => {
    expect(error('')).toBe('Ingresá el importe')
    expect(error('  $ ')).toBe('Ingresá el importe')
  })

  it('rechaza el cero y los negativos', () => {
    expect(error('0')).toBe('El importe tiene que ser mayor que cero')
    expect(error('0,00')).toBe('El importe tiene que ser mayor que cero')
    expect(error('-5')).toBe('El importe tiene que ser mayor que cero')
  })

  it('rechaza más de dos decimales en lugar de redondear', () => {
    expect(error('12,345,6')).toBe('Escribí el importe con números, como 1.234,56')
    expect(error('1.234,567')).toBe('Usá hasta dos decimales')
  })

  it('rechaza lo que no se puede leer sin adivinar', () => {
    for (const text of ['abc', '12 34', '1.2.3,4', '0.500', '12.34.56', '1,23,456', ',5', '5,', 'US$ 5', '1e3']) {
      expect(error(text), text).toBe('Escribí el importe con números, como 1.234,56')
    }
  })

  it('rechaza más de diez cifras enteras', () => {
    expect(amount('9999999999,99')).toBe('9999999999.99')
    expect(error('12345678901')).toBe('Revisá el importe: admite hasta 10 cifras antes de la coma')
  })
})

describe('toAmountInput', () => {
  it('escribe el importe de la API con coma decimal, y se vuelve a leer igual', () => {
    expect(toAmountInput('1234.56')).toBe('1234,56')
    expect(amount(toAmountInput('1234.56'))).toBe('1234.56')
    expect(amount(toAmountInput('1234.00'))).toBe('1234.00')
    expect(amount(toAmountInput('0.50'))).toBe('0.50')
  })
})
