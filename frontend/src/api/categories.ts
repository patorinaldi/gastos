import type {
  CategoryResponse,
  CategoryRuleResponse,
  CreateCategoryRuleRequest,
  CreateCategoryRuleResponse,
  SaveCategoryRequest,
} from '../types/api.ts'
import { apiRequest } from './client.ts'

/** M4, categorización. Piden sesión: el hogar es siempre el de quien consulta. */

/** Las categorías activas, por nombre: las que se ofrecen para clasificar (RF-24). */
export function listCategories() {
  return apiRequest<CategoryResponse[]>('/api/categories')
}

/**
 * RF-24, HU-12. El servidor guarda el nombre sin los espacios de los extremos. Uno que ya tiene otra
 * categoría activa del hogar, sin distinguir mayúsculas, responde 409 (RN-09).
 */
export function createCategory(name: string) {
  return apiRequest<CategoryResponse>('/api/categories', {
    method: 'POST',
    body: { name } satisfies SaveCategoryRequest,
  })
}

/**
 * El nombre vive en una sola fila: el cambio se ve en todos los gastos de la categoría, también en los
 * de meses anteriores. Las reglas siguen apuntando a ella. Mismo 409 que el alta.
 */
export function renameCategory(id: string, name: string) {
  return apiRequest<CategoryResponse>(`/api/categories/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: { name } satisfies SaveCategoryRequest,
  })
}

/**
 * Baja lógica (RN-16): deja de ofrecerse y sus reglas se eliminan, así que no se asigna a gastos
 * nuevos. Los que ya la tienen la conservan, en el historial y en el análisis.
 */
export function deactivateCategory(id: string) {
  return apiRequest<void>(`/api/categories/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

/**
 * RF-23, HU-11. Crea la regla y clasifica con ella los gastos de la bandeja que coinciden; la
 * respuesta dice cuántos. Un patrón repetido en el hogar responde 409 (RN-10).
 */
export function createCategoryRule(request: CreateCategoryRuleRequest) {
  return apiRequest<CreateCategoryRuleResponse>('/api/category-rules', { method: 'POST', body: request })
}

/** RF-43. Las reglas que aplican, las de categorías activas, por patrón. */
export function listCategoryRules() {
  return apiRequest<CategoryRuleResponse[]>('/api/category-rules')
}

/**
 * RF-43. Los gastos que la regla ya clasificó conservan su categoría; los próximos de ese comercio
 * van a la bandeja si ninguna otra regla coincide.
 */
export function deleteCategoryRule(id: string) {
  return apiRequest<void>(`/api/category-rules/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
