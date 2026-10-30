import type { CategoryResponse } from '../types/api.ts'
import { apiRequest } from './client.ts'

/** M4, categorización. Piden sesión: el hogar es siempre el de quien consulta. */

/** Las categorías activas, por nombre: las que se ofrecen para clasificar (RF-24). */
export function listCategories() {
  return apiRequest<CategoryResponse[]>('/api/categories')
}
