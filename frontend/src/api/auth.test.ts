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
