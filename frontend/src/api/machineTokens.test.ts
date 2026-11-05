import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryStorage } from '../test-support.ts'

const fetchMock = vi.fn<typeof fetch>()

// El cliente lee el token de la sesión: se cargan juntos y de nuevo en cada prueba.
async function load() {
  vi.resetModules()
  const session = await import('../session.ts')
  const tokens = await import('./machineTokens.ts')
  session.saveSession({ token: 'token-de-sesion', expiresAt: '2099-01-01T00:00:00Z' })
  return tokens
}

function lastCall() {
  const [url, init] = fetchMock.mock.calls[0]
  const headers = init?.headers as Record<string, string>
  return {
    url,
    method: init?.method,
    body: init?.body === undefined ? undefined : JSON.parse(String(init.body)),
    authorization: headers.Authorization,
  }
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('localStorage', new MemoryStorage())
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('tokens de cliente máquina', () => {
  it('emite con el nombre y la contraseña actual, con la sesión', async () => {
    const { issueMachineToken } = await load()
    const created = { id: 't1', name: 'Atajo', token: 'gst_abc', createdAt: '2026-11-05T12:00:00Z' }
    fetchMock.mockResolvedValue(Response.json(created, { status: 201 }))

    await expect(issueMachineToken('Atajo', 'contraseña-actual')).resolves.toEqual(created)

    expect(lastCall()).toEqual({
      url: 'http://localhost:8080/api/auth/machine-tokens',
      method: 'POST',
      body: { name: 'Atajo', password: 'contraseña-actual' },
      authorization: 'Bearer token-de-sesion',
    })
  })

  it('lista los tokens con la sesión', async () => {
    const { listMachineTokens } = await load()
    fetchMock.mockResolvedValue(Response.json([]))

    await expect(listMachineTokens()).resolves.toEqual([])

    expect(lastCall()).toMatchObject({
      url: 'http://localhost:8080/api/auth/machine-tokens',
      method: 'GET',
      authorization: 'Bearer token-de-sesion',
    })
  })

  it('revoca por identificador', async () => {
    const { revokeMachineToken } = await load()
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await expect(revokeMachineToken('5b1d2c3e-0000-4000-8000-000000000001')).resolves.toBeUndefined()

    expect(lastCall()).toMatchObject({
      url: 'http://localhost:8080/api/auth/machine-tokens/5b1d2c3e-0000-4000-8000-000000000001',
      method: 'DELETE',
    })
  })

  // Una contraseña incorrecta es un 403, no un 401: la sesión sigue, y el mensaje es del servidor.
  it('una contraseña incorrecta llega como 403 sin cerrar la sesión', async () => {
    const { issueMachineToken } = await load()
    const { ApiError } = await import('./client.ts')
    const { getSession } = await import('../session.ts')
    fetchMock.mockResolvedValue(
      Response.json({ status: 403, detail: 'La contraseña no es correcta' }, { status: 403 }),
    )

    const failure = await issueMachineToken('Atajo', 'otra').catch((error: unknown) => error)

    expect(failure).toBeInstanceOf(ApiError)
    expect((failure as InstanceType<typeof ApiError>).status).toBe(403)
    expect(getSession()).not.toBeNull()
  })
})
