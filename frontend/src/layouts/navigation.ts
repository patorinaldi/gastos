/** Secciones de la app autenticada (M7 en modulos.md). El orden es el de la navegación. */
export const SECTIONS = [
  { to: '/', label: 'Tablero', end: true },
  // end: true para que "Movimientos" no se marque como página actual en /expenses/new.
  { to: '/expenses', label: 'Movimientos', end: true },
  { to: '/inbox', label: 'Bandeja', end: false },
  { to: '/household', label: 'Hogar', end: false },
] as const

/** Alta de un gasto: la acción principal de la app (RNF-14). */
export const NEW_EXPENSE_PATH = '/expenses/new'
/**
 * A dónde vuelve una pantalla al terminar: la que la abrió, si pasó su dirección en el estado de la
 * navegación (`state={{ back: '/expenses?month=2026-09' }}`), o `fallback`. Solo acepta direcciones
 * de esta app: el estado lo puede armar cualquier enlace, y "//otro.sitio" llevaría afuera.
 */
export function backPath(state: unknown, fallback: string): string {
  if (typeof state === 'object' && state !== null && 'back' in state && typeof state.back === 'string') {
    const back = state.back
    if (back.startsWith('/') && !back.startsWith('//')) {
      return back
    }
  }
  return fallback
}
