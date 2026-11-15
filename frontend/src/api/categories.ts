import type {
  CategoryResponse,
  CategoryRuleResponse,
  CreateCategoryRuleRequest,
  CreateCategoryRuleResponse,
} from '../types/api.ts'
import { apiRequest } from './client.ts'

/** M4, categorización. Piden sesión: el hogar es siempre el de quien consulta. */

/** Las categorías activas, por nombre: las que se ofrecen para clasificar (RF-24). */
export function listCategories() {
  return apiRequest<CategoryResponse[]>('/api/categories')
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
