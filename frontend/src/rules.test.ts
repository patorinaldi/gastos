import { describe, expect, it } from 'vitest'
import { groupRules } from './rules.ts'

const rule = (id: string, pattern: string, categoryId: string, categoryName: string) => ({
  id,
  pattern,
  categoryId,
  categoryName,
})

const rules = [
  rule('1', 'carrefour', 's', 'Supermercado'),
  rule('2', 'coto', 's', 'Supermercado'),
  rule('3', 'uber', 't', 'Transporte'),
  rule('4', 'farmacity', 'f', 'Salud'),
  rule('5', 'uber eats', 'r', 'Restaurantes y delivery'),
]

describe('reglas agrupadas por categoría', () => {
  it('agrupa por categoría, ordenadas por nombre, y cada grupo por patrón', () => {
    expect(groupRules(rules, '').map((g) => [g.categoryName, g.rules.map((r) => r.pattern)])).toEqual([
      ['Restaurantes y delivery', ['uber eats']],
      ['Salud', ['farmacity']],
      ['Supermercado', ['carrefour', 'coto']],
      ['Transporte', ['uber']],
    ])
  })

  it('filtra por patrón, sin distinguir mayúsculas ni tildes', () => {
    expect(groupRules(rules, ' UBÉR ').map((g) => g.rules.map((r) => r.pattern))).toEqual([['uber eats'], ['uber']])
  })

  it('filtra por patrón por su clave, sin espacios ni signos, como el servidor', () => {
    expect(groupRules(rules, 'ubereats').map((g) => g.rules.map((r) => r.pattern))).toEqual([['uber eats']])
    expect(groupRules(rules, 'uber-eats').map((g) => g.rules.map((r) => r.pattern))).toEqual([['uber eats']])
  })

  it('una búsqueda solo de signos no coincide con todos los patrones', () => {
    expect(groupRules(rules, '*')).toEqual([])
  })

  it('filtra por categoría: muestra todas sus reglas', () => {
    expect(groupRules(rules, 'super').map((g) => [g.categoryName, g.rules.length])).toEqual([['Supermercado', 2]])
  })

  it('sin coincidencias, no hay grupos', () => {
    expect(groupRules(rules, 'kiosco')).toEqual([])
  })
})
