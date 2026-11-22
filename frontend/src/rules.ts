import { normalizePattern, patternKey } from './patterns.ts'
import type { CategoryRuleResponse } from './types/api.ts'

/*
 * El listado de reglas como lo muestra su pantalla (RF-43): agrupado por categoría, porque así se
 * revisa qué comercios van a cada una, y con un filtro, porque el catálogo inicial ya trae 43.
 */

export interface RuleGroup {
  categoryId: string
  categoryName: string
  rules: CategoryRuleResponse[]
}

const collator = new Intl.Collator('es', { sensitivity: 'base' })

/**
 * Las reglas por categoría, las categorías por nombre y las reglas de cada una por patrón.
 *
 * Con `query`, quedan las reglas cuyo patrón lo contiene y todas las de las categorías cuyo nombre lo
 * contiene. El patrón se compara por su clave, como lo compara el servidor (sin mayúsculas, tildes,
 * espacios ni signos), así "Ubér" encuentra "uber" y "burgerking" encuentra "burger king". El nombre de
 * la categoría, en la forma de los patrones (minúscula, sin tildes).
 */
export function groupRules(rules: CategoryRuleResponse[], query: string): RuleGroup[] {
  const wanted = normalizePattern(query)
  // Una búsqueda solo de signos no tiene clave: no busca por patrón, solo por nombre de categoría.
  const wantedKey = patternKey(query)
  const groups = new Map<string, RuleGroup>()
  for (const rule of rules) {
    const shown =
      wanted === '' ||
      (wantedKey !== '' && patternKey(rule.pattern).includes(wantedKey)) ||
      normalizePattern(rule.categoryName).includes(wanted)
    if (!shown) {
      continue
    }
    const group = groups.get(rule.categoryId) ?? { categoryId: rule.categoryId, categoryName: rule.categoryName, rules: [] }
    group.rules.push(rule)
    groups.set(rule.categoryId, group)
  }
  return [...groups.values()]
    .sort((a, b) => collator.compare(a.categoryName, b.categoryName))
    .map((group) => ({ ...group, rules: group.rules.sort((a, b) => collator.compare(a.pattern, b.pattern)) }))
}
