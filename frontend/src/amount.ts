import type { Money } from './types/api.ts'

/*
 * El importe que se escribe en un formulario, llevado al formato de la API: "1.234,56" → "1234.56".
 *
 * Sigue las mismas reglas que AmountParser del backend, el que normaliza la captura rápida (RF-32),
 * para que un importe se lea igual lo cargue la pantalla o el atajo del teléfono. Ante la duda
 * rechaza: un importe mal interpretado no da error, queda registrado y sale en los totales (RN-06).
 *
 * - Con los dos separadores, el último es el decimal y el otro tiene que agrupar de a tres
 *   ("1.234,56", "1,234.56").
 * - Con un solo separador que aparece una vez, seguido de uno o dos dígitos es decimal ("12,5"), y
 *   seguido de tres es de miles ("1.234"). Leído como decimal, "1.234" tendría tres decimales, que
 *   RN-06 no admite.
 * - Un separador repetido es siempre de miles ("1.234.567").
 * - Un grupo de miles que empieza en cero, más de dos decimales, signos u otras monedas se rechazan.
 *
 * El importe nunca pasa por Number: se arma como texto, y así no pierde centavos (RD-01).
 */

/** Dígitos enteros que admite la columna numeric(12, 2). */
const MAX_INTEGER_DIGITS = 10

// \p{Z} incluye el espacio duro y los espacios finos.
const EDGE_SPACES = /^[\p{Z}\s]+|[\p{Z}\s]+$/gu
const CURRENCY = /^(?:AR\$|ARS|\$)|ARS$/gi
const DIGITS_AND_SEPARATORS = /^[0-9.,]+$/
const PLAIN_DIGITS = /^[0-9]+$/

export type AmountResult = { ok: true; amount: Money } | { ok: false; error: string }

/** Los mensajes dicen qué corregir (RNF-17). */
const ERRORS = {
  empty: 'Ingresá el importe',
  notPositive: 'El importe tiene que ser mayor que cero',
  format: 'Escribí el importe con números, como 1.234,56',
  decimals: 'Usá hasta dos decimales',
  tooLarge: `Revisá el importe: admite hasta ${MAX_INTEGER_DIGITS} cifras antes de la coma`,
}

class InvalidAmount extends Error {}

export function parseAmount(raw: string): AmountResult {
  const text = trim(trim(raw).replace(CURRENCY, ''))
  if (text === '') {
    return { ok: false, error: ERRORS.empty }
  }
  if (text.startsWith('-')) {
    return { ok: false, error: ERRORS.notPositive }
  }
  if (!DIGITS_AND_SEPARATORS.test(text)) {
    return { ok: false, error: ERRORS.format }
  }

  let normalized: { integer: string; fraction: string }
  try {
    normalized = normalize(text)
  } catch (error) {
    return { ok: false, error: error instanceof InvalidAmount ? error.message : ERRORS.format }
  }

  const integer = normalized.integer.replace(/^0+(?=\d)/, '')
  const fraction = normalized.fraction.padEnd(2, '0')
  if (/^0+$/.test(integer + fraction)) {
    return { ok: false, error: ERRORS.notPositive }
  }
  if (integer.length > MAX_INTEGER_DIGITS) {
    return { ok: false, error: ERRORS.tooLarge }
  }
  return { ok: true, amount: `${integer}.${fraction}` }
}

/** Sin separador de miles y con el decimal aparte. */
function normalize(text: string): { integer: string; fraction: string } {
  const dots = count(text, '.')
  const commas = count(text, ',')
  if (dots === 0 && commas === 0) {
    return { integer: text, fraction: '' }
  }

  let decimal: string | null
  let grouping: string | null
  if (dots > 0 && commas > 0) {
    decimal = text.lastIndexOf('.') > text.lastIndexOf(',') ? '.' : ','
    grouping = decimal === '.' ? ',' : '.'
    if (count(text, decimal) > 1) {
      throw new InvalidAmount(ERRORS.format)
    }
  } else {
    const separator = dots > 0 ? '.' : ','
    const digitsAfter = text.length - text.lastIndexOf(separator) - 1
    const isDecimal = Math.max(dots, commas) === 1 && digitsAfter !== 3
    decimal = isDecimal ? separator : null
    grouping = isDecimal ? null : separator
  }

  const decimalAt = decimal === null ? -1 : text.indexOf(decimal)
  const integerPart = decimalAt < 0 ? text : text.slice(0, decimalAt)
  const fraction = decimalAt < 0 ? '' : text.slice(decimalAt + 1)
  if (decimalAt >= 0 && !PLAIN_DIGITS.test(fraction)) {
    throw new InvalidAmount(ERRORS.format)
  }
  if (fraction.length > 2) {
    throw new InvalidAmount(ERRORS.decimals)
  }
  return { integer: integerDigits(integerPart, grouping), fraction }
}

/** La parte entera sin separadores de miles, que tienen que agrupar de a tres. */
function integerDigits(integerPart: string, grouping: string | null): string {
  if (PLAIN_DIGITS.test(integerPart)) {
    return integerPart
  }
  if (grouping === null) {
    throw new InvalidAmount(ERRORS.format)
  }
  const group = grouping === '.' ? '\\.' : ','
  if (!new RegExp(`^[1-9][0-9]{0,2}(?:${group}[0-9]{3})+$`).test(integerPart)) {
    throw new InvalidAmount(ERRORS.format)
  }
  return integerPart.split(grouping).join('')
}

function trim(text: string): string {
  return text.replace(EDGE_SPACES, '')
}

function count(text: string, char: string): number {
  return text.split(char).length - 1
}

/**
 * El importe de la API como se escribe en el formulario: "1234.56" → "1234,56". Sin separador de
 * miles, para que editarlo no obligue a acomodar los puntos. Vuelve a leerse igual con parseAmount.
 */
export function toAmountInput(amount: Money): string {
  return amount.replace('.', ',')
}
