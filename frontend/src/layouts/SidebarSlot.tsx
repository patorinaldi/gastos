import { useContext, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { SidebarSlotContext } from './sidebarSlotContext.ts'

/**
 * Lo que la pantalla actual suma a la barra lateral del shell, debajo de la navegación. Así el
 * tablero lleva su resumen a la barra en escritorio, y la app sigue teniendo una sola barra lateral.
 *
 * El shell da el lugar por contexto; la pantalla le manda su contenido con un portal. Mientras el
 * shell no lo montó, no se dibuja nada.
 */
export function SidebarContent({ children }: { children: ReactNode }) {
  const slot = useContext(SidebarSlotContext)
  return slot ? createPortal(children, slot) : null
}