import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useSession } from '../session.ts'

/**
 * Deja pasar solo con sesión; sin ella, manda al login y le pasa la ruta pedida para volver
 * después. Como lee la sesión con useSession, también actúa cuando la sesión se cierra con la
 * pantalla abierta: al cerrar sesión, en otra pestaña o ante un 401 de la API.
 */
export function RequireSession({ children }: { children: ReactNode }) {
  const session = useSession()
  const { pathname, search, hash } = useLocation()

  if (!session) {
    return <Navigate to="/login" replace state={{ from: pathname + search + hash }} />
  }
  return children
}
