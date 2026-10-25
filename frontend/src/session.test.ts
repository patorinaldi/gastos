import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { BlockedStorage, MemoryStorage } from './test-support.ts'

const KEY = 'gastos.session'
const FUTURE = '2099-01-01T00:00:00Z'
const PAST = '2000-01-01T00:00:00Z'

let storage: MemoryStorage

// El módulo lee lo guardado al cargarse, como al abrir la página: cada prueba lo carga de nuevo.
async function loadSession() {
  vi.resetModules()
  return import('./session.ts')
}

beforeEach(() => {
  storage = new MemoryStorage()
  vi.stubGlobal('localStorage', storage)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('sesión', () => {
  it('guarda la sesión y la recupera al volver a abrir la página', async () => {
    const first = await loadSession()
    first.saveSession({ token: 'token-de-prueba', expiresAt: FUTURE })

    const reopened = await loadSession()
    expect(reopened.getSession()).toEqual({ token: 'token-de-prueba', expiresAt: FUTURE })
  })

  it('no devuelve una sesión vencida', async () => {
    storage.setItem(KEY, JSON.stringify({ token: 'token-de-prueba', expiresAt: PAST }))
    const { getSession } = await loadSession()
    expect(getSession()).toBeNull()
  })

  it('vence en el instante de expiresAt', async () => {
    const { getSession, saveSession } = await loadSession()
    saveSession({ token: 'token-de-prueba', expiresAt: '2026-10-23T12:00:00Z' })
    expect(getSession(Date.parse('2026-10-23T11:59:59Z'))).not.toBeNull()
    expect(getSession(Date.parse('2026-10-23T12:00:00Z'))).toBeNull()
  })

  it('descarta lo guardado que no tiene la forma de una sesión', async () => {
    for (const raw of ['no es json', '{}', '{"token":"","expiresAt":"2099-01-01T00:00:00Z"}', '{"token":"t","expiresAt":"mañana"}']) {
      storage.setItem(KEY, raw)
      const { getSession } = await loadSession()
      expect(getSession(), raw).toBeNull()
    }
  })

  it('cerrar sesión la borra del almacenamiento', async () => {
    const { clearSession, getSession, saveSession } = await loadSession()
    saveSession({ token: 'token-de-prueba', expiresAt: FUTURE })
    clearSession()
    expect(getSession()).toBeNull()
    expect(storage.getItem(KEY)).toBeNull()
  })

  it('un 401 de un token viejo no cierra la sesión nueva', async () => {
    const { clearSessionIfToken, getSession, saveSession } = await loadSession()
    saveSession({ token: 'token-nuevo', expiresAt: FUTURE })
    clearSessionIfToken('token-viejo')
    expect(getSession()?.token).toBe('token-nuevo')
    clearSessionIfToken('token-nuevo')
    expect(getSession()).toBeNull()
  })

  it('devuelve el mismo objeto mientras la sesión no cambia', async () => {
    const { getSession, saveSession } = await loadSession()
    saveSession({ token: 'token-de-prueba', expiresAt: FUTURE })
    expect(getSession()).toBe(getSession())
  })

  it('sin almacenamiento disponible, la sesión vive en memoria', async () => {
    vi.stubGlobal('localStorage', new BlockedStorage())
    const { getSession, saveSession } = await loadSession()
    expect(getSession()).toBeNull()
    saveSession({ token: 'token-de-prueba', expiresAt: FUTURE })
    expect(getSession()?.token).toBe('token-de-prueba')
  })
})
