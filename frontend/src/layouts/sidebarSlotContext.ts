import { createContext } from 'react'

/**
 * El lugar de la barra lateral que la pantalla actual puede llenar (ver SidebarSlot). Null mientras
 * el shell no lo montó.
 */
export const SidebarSlotContext = createContext<HTMLElement | null>(null)