import { useSyncExternalStore } from 'react'
import type { IsoInstant, LoginResponse } from './types/api.ts'

/*
 * Sesión del cliente web: el token que emite el login y su vencimiento.
 *
 * Se guarda en localStorage para que sobreviva a cerrar la pestaña. El token dura 7 días
 * precisamente para no pedir el inicio de sesión a diario desde el teléfono (RNF-29), y en
 * sessionStorage se perdería cada vez que el navegador del teléfono descarta la pestaña. La
 * contracara es que cualquier script que corra en la página puede leerlo: la defensa es no ejecutar
 * código ajeno (React escapa todo lo que muestra, y el build no carga scripts de terceros), y el
 * servidor puede invalidar todas las sesiones de un usuario si su token se filtra.
 *
 * El hogar no se guarda: aunque el token lo lleve, queda desactualizado si el usuario se cambia de
 * hogar. Se pide a GET /api/auth/me (modulos.md, M1).
 *
 * Si el navegador no deja usar localStorage (modo privado estricto, almacenamiento bloqueado), la
 * sesión vive solo en memoria: se puede usar la app, pero recargar la página pide iniciar sesión.
 */

export interface Session {
  token: string
  expiresAt: IsoInstant
}

const STORAGE_KEY = 'gastos.session'

let current: Session | null = read()
const listeners = new Set<() => void>()

function storage(): Storage | null {
  try {
    return globalThis.localStorage ?? null
  } catch {
    return null
  }
}

/** Lo guardado, si tiene la forma de una sesión. Un valor corrupto o de otra versión se descarta. */
function read(): Session | null {
  let raw: string | null
  try {
    raw = storage()?.getItem(STORAGE_KEY) ?? null
  } catch {
    return null
  }
  if (!raw) {
    return null
  }
  try {
    const value: unknown = JSON.parse(raw)
    if (
      typeof value === 'object' && value !== null &&
      'token' in value && typeof value.token === 'string' && value.token !== '' &&
      'expiresAt' in value && typeof value.expiresAt === 'string' && !Number.isNaN(Date.parse(value.expiresAt))
    ) {
      return { token: value.token, expiresAt: value.expiresAt }
    }
  } catch {
    // JSON inválido: se trata como si no hubiera sesión.
  }
  return null
}

function write(session: Session | null) {
  try {
    const store = storage()
    if (session) {
      store?.setItem(STORAGE_KEY, JSON.stringify(session))
    } else {
      store?.removeItem(STORAGE_KEY)
    }
  } catch {
    // Sin almacenamiento disponible la sesión queda solo en memoria.
  }
}

function set(session: Session | null) {
  current = session
  write(session)
  listeners.forEach((listener) => listener())
}

/**
 * La sesión vigente, o null si no hay o ya venció. Devuelve siempre el mismo objeto mientras la
 * sesión no cambie, que es lo que necesita useSyncExternalStore.
 */
export function getSession(now: number = Date.now()): Session | null {
  if (current && Date.parse(current.expiresAt) <= now) {
    return null
  }
  return current
}

export function saveSession({ token, expiresAt }: LoginResponse) {
  set({ token, expiresAt })
}

/**
 * Cerrar sesión en este dispositivo es descartar el token: el servidor no guarda sesiones
 * (modulos.md, M1).
 */
export function clearSession() {
  set(null)
}

/**
 * Descarta la sesión solo si sigue siendo la del token indicado. Lo usa el cliente de la API ante
 * un 401: si mientras tanto se inició otra sesión, la respuesta vieja no tiene que cerrarla.
 */
export function clearSessionIfToken(token: string) {
  if (current?.token === token) {
    set(null)
  }
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

// Iniciar o cerrar sesión en otra pestaña la cambia también en esta.
globalThis.addEventListener?.('storage', (event: StorageEvent) => {
  if (event.key === STORAGE_KEY || event.key === null) {
    current = read()
    listeners.forEach((listener) => listener())
  }
})

/** La sesión vigente, y la pantalla se vuelve a dibujar cuando se inicia o se cierra. */
export function useSession(): Session | null {
  return useSyncExternalStore(subscribe, () => getSession())
}
