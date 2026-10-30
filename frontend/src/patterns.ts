/*
 * Cómo se escribe un patrón de regla y cómo se compara con un comercio (M4, RF-20). Espejo de
 * Patterns y MerchantKey del backend: si cambia allá, cambia acá.
 *
 * La bandeja lo usa para agrupar los gastos del mismo comercio aunque estén escritos distinto
 * ("Almacén de Pepe", "ALMACEN DE PEPE"), y para anticipar a cuántos gastos alcanza una regla antes de
 * crearla. Lo que decide es el servidor: esto es para que el usuario sepa qué va a pasar.
 */

/** Letras o números que tiene que tener, como mínimo, la clave de un patrón (Patterns.MIN_KEY_LENGTH). */
const MIN_KEY_LENGTH = 3

/** Largo máximo de la forma guardada: la columna es varchar(200). */
const MAX_LENGTH = 200

const MARKS = /\p{M}+/gu
const SEPARATORS = /[\p{Z}\s\p{Cc}\p{Cf}]+/gu
const NOT_LETTER_OR_DIGIT = /[^\p{L}\p{N}]+/gu
const WORD = /[\p{L}\p{N}]+/gu

/** La forma en que se guarda un patrón: minúscula, sin tildes, con un solo espacio entre palabras. */
export function normalizePattern(text: string): string {
  return text.normalize('NFKD').replace(MARKS, '').toLowerCase().replace(SEPARATORS, ' ').trim()
}

/** La clave con la que se compara: la forma guardada solo con letras y números. */
export function patternKey(text: string): string {
  return normalizePattern(text).replace(NOT_LETTER_OR_DIGIT, '')
}

/**
 * Si el patrón coincide con el comercio: su clave aparece en la del comercio empezando al principio de
 * una palabra. Así "uber" no coincide con "Pub Ernesto", pero "supermercado" sí con "Super Mercado".
 */
export function matchesPattern(merchant: string, pattern: string): boolean {
  const target = patternKey(pattern)
  if (target === '') {
    return false
  }
  let key = ''
  const wordStarts = new Set<number>()
  for (const [word] of normalizePattern(merchant).matchAll(WORD)) {
    wordStarts.add(key.length)
    key += word
  }
  for (let at = key.indexOf(target); at >= 0; at = key.indexOf(target, at + 1)) {
    if (wordStarts.has(at)) {
      return true
    }
  }
  return false
}

/** El error del patrón, con lo que hay que corregir (RNF-17), o undefined si sirve. */
export function validatePattern(pattern: string): string | undefined {
  if (patternKey(pattern).length < MIN_KEY_LENGTH) {
    return `Usá al menos ${MIN_KEY_LENGTH} letras o números`
  }
  if (normalizePattern(pattern).length > MAX_LENGTH) {
    return `Usá un patrón de hasta ${MAX_LENGTH} caracteres`
  }
  return undefined
}
