import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryStorage } from '../test-support.ts'

const fetchMock = vi.fn<typeof fetch>()

async function load() {
  vi.resetModules()
  return import('./auth.ts')
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('localStorage', new MemoryStorage())
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('tokenFromHash', () => {
  it('lee el token del fragmento del enlace', async () => {
    const { tokenFromHash } = await load()
    expect(tokenFromHash('#token=abc123')).toBe('abc123')
    expect(tokenFromHash('token=abc123')).toBe('abc123')
  })

  it('sin token, o con uno vacío, no hay token', async () => {
    const { tokenFromHash } = await load()
    expect(tokenFromHash('')).toBeNull()
    expect(tokenFromHash('#')).toBeNull()
    expect(tokenFromHash('#token=')).toBeNull()
    expect(tokenFromHash('#otro=abc')).toBeNull()
  })
})

describe('resendVerification', () => {
  it('pide el reenvío con el correo, sin token de sesión', async () => {
    const { resendVerification } = await load()
    fetchMock.mockResolvedValue(new Response(null, { status: 202 }))

    await expect(resendVerification('ana@ejemplo.com')).resolves.toBeUndefined()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/auth/verify/resend')
    expect(init?.body).toBe('{"email":"ana@ejemplo.com"}')
    const headers = init?.headers as Record<string, string>
    expect(headers.Authorization).toBeUndefined()
  })
})

describe('forgotPassword', () => {
  it('pide el enlace con el correo, sin token de sesión', async () => {
    const { forgotPassword } = await load()
    fetchMock.mockResolvedValue(new Response(null, { status: 202 }))

    await expect(forgotPassword('ana@ejemplo.com')).resolves.toBeUndefined()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/auth/password/forgot')
    expect(init?.method).toBe('POST')
    expect(init?.body).toBe('{"email":"ana@ejemplo.com"}')
    const headers = init?.headers as Record<string, string>
    expect(headers.Authorization).toBeUndefined()
  })
})

describe('resetPassword', () => {
  it('cambia la contraseña con el token del enlace, sin token de sesión', async () => {
    const { resetPassword } = await load()
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await expect(resetPassword('token-del-enlace', 'contraseña-nueva')).resolves.toBeUndefined()

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/auth/password/reset')
    expect(init?.body).toBe('{"token":"token-del-enlace","password":"contraseña-nueva"}')
    const headers = init?.headers as Record<string, string>
    expect(headers.Authorization).toBeUndefined()
  })

  // Un enlace vencido o usado responde 410: la pantalla lo distingue para ofrecer pedir otro.
  it('un enlace vencido o usado llega como un error con su estado', async () => {
    const { resetPassword } = await load()
    const { ApiError } = await import('./client.ts')
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ status: 410, detail: 'El enlace no es válido o ya venció. Pedí uno nuevo.' }), {
        status: 410,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )

    const failure = await resetPassword('token-vencido', 'contraseña-nueva').catch((error: unknown) => error)

    expect(failure).toBeInstanceOf(ApiError)
    expect((failure as InstanceType<typeof ApiError>).status).toBe(410)
  })
})

describe('verifyEmail', () => {
  it('pide la confirmación de un token una sola vez aunque se llame dos veces', async () => {
    const { verifyEmail } = await load()
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await Promise.all([verifyEmail('token-de-prueba'), verifyEmail('token-de-prueba')])

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock.mock.calls[0][1]?.body).toBe('{"token":"token-de-prueba"}')
  })

  it('si falla, el reintento vuelve a pedirla', async () => {
    const { verifyEmail } = await load()
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(verifyEmail('token-de-prueba')).rejects.toMatchObject({ status: 0 })
    await expect(verifyEmail('token-de-prueba')).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
