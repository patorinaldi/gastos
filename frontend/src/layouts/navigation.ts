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