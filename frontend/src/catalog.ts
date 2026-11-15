import type { CategoryResponse } from './types/api.ts'

/*
 * El catálogo de categorías como lo muestra su pantalla (RF-24). La lista se pide una vez y después se
 * actualiza con las respuestas del servidor: volver a pedirla en cada cambio dibujaría la carga y se
 * llevaría el foco de la fila que se acaba de editar.
 */

const collator = new Intl.Collator('es', { sensitivity: 'base' })

/** Por nombre, como las devuelve el servidor: sin distinguir mayúsculas ni tildes. */
export function sortByName(categories: CategoryResponse[]): CategoryResponse[] {
  return [...categories].sort((a, b) => collator.compare(a.name, b.name))
}

/** Con la categoría nueva, o con el nombre nuevo de una que ya estaba, en su lugar por nombre. */
export function withCategory(categories: CategoryResponse[], saved: CategoryResponse): CategoryResponse[] {
  return sortByName([...categories.filter((category) => category.id !== saved.id), saved])
}

/** Sin la categoría dada de baja. */
export function withoutCategory(categories: CategoryResponse[], id: string): CategoryResponse[] {
  return categories.filter((category) => category.id !== id)
}
