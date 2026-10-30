import { describe, expect, it } from 'vitest'
import { matchesPattern, normalizePattern, patternKey, validatePattern } from './patterns.ts'

// Casos de RuleSetTest del backend: un comercio tiene que coincidir igual en los dos lados.
describe('normalizePattern', () => {
  it('pasa a minúscula, quita las tildes y deja un solo espacio', () => {
    expect(normalizePattern('  Almacén   de PEPE ')).toBe('almacen de pepe')
    expect(normalizePattern('Ñandú')).toBe('nandu')
  })
})

describe('patternKey', () => {
  it('deja solo letras y números', () => {
    expect(patternKey("McDonald's")).toBe('mcdonalds')
    expect(patternKey('Pedidos Ya')).toBe('pedidosya')
  })
})

describe('matchesPattern', () => {
  it('coincide aunque cambien los espacios, la puntuación o las tildes', () => {
    expect(matchesPattern('Super Mercado Rosario', 'supermercado')).toBe(true)
    expect(matchesPattern('PEDIDOS YA', 'pedidosya')).toBe(true)
    expect(matchesPattern("McDonald's Centro", 'mcdonald')).toBe(true)
    expect(matchesPattern('ALMACEN DE PEPE', 'Almacén de Pepe')).toBe(true)
  })

  it('puede terminar en medio de una palabra', () => {
    expect(matchesPattern('Farmacias del Pueblo', 'farmacia')).toBe(true)
  })

  it('tiene que empezar al principio de una palabra', () => {
    expect(matchesPattern('Pub Ernesto', 'uber')).toBe(false)
    expect(matchesPattern('Tuberías del Sur', 'uber')).toBe(false)
    expect(matchesPattern('Disco Tomás', 'coto')).toBe(false)
    expect(matchesPattern('UBER *TRIP', 'uber')).toBe(true)
  })

  it('un patrón sin letras ni números no coincide con nada', () => {
    expect(matchesPattern('Kiosco', '***')).toBe(false)
  })
})

describe('validatePattern', () => {
  it('pide al menos tres letras o números', () => {
    expect(validatePattern('ab')).toBe('Usá al menos 3 letras o números')
    expect(validatePattern('a.b')).toBe('Usá al menos 3 letras o números')
    expect(validatePattern('ypf')).toBeUndefined()
  })

  it('admite hasta 200 caracteres ya normalizado', () => {
    expect(validatePattern('a'.repeat(200))).toBeUndefined()
    expect(validatePattern('a'.repeat(201))).toMatch(/hasta 200/)
  })
})
