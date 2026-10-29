import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryStorage } from '../test-support.ts'

const fetchMock = vi.fn<typeof fetch>()

// El cliente lee el token de la sesión: se cargan juntos y de nuevo en cada prueba.
async function load() {
  vi.resetModules()
  const session = await import('../session.ts')
  const client = await import('./client.ts')
  const analysis = await import('./analysis.ts')
  return { ...session, ...client, ...analysis }
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('localStorage', new MemoryStorage())
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('query', () => {
  it('manda solo los filtros que vienen', async () => {
    const { query } = await load()
    expect(query({ from: '2026-10-01', to: '2026-10-31' })).toBe('?from=2026-10-01&to=2026-10-31')
    expect(query({ from: '2026-10-01', to: '2026-10-31', categoryId: undefined, paymentMethod: 'Tarjeta' }))
      .toBe('?from=2026-10-01&to=2026-10-31&paymentMethod=Tarjeta')
  })

  it('sin filtros no agrega parámetros', async () => {
    const { query } = await load()
    expect(query({})).toBe('')
    expect(query({ categoryId: '' })).toBe('')
  })

  it('codifica los valores', async () => {
    const { query } = await load()
    expect(query({ categoryId: 'a b&c' })).toBe('?categoryId=a+b%26c')
  })

  it('manda los números como texto, también el cero', async () => {
    const { query } = await load()
    expect(query({ page: 0, size: 10 })).toBe('?page=0&size=10')
  })
})

describe('getSummary', () => {
  it('pide el resumen del período con el token de sesión', async () => {
    const { getSummary, saveSession } = await load()
    saveSession({ token: 'token-de-prueba', expiresAt: '2099-01-01T00:00:00Z' })
    const summary = { total: '0.00', byCategory: [], byOwner: [], byPaymentMethod: [] }
    fetchMock.mockResolvedValue(Response.json(summary))

    await expect(getSummary({ from: '2026-10-01', to: '2026-10-31' })).resolves.toEqual(summary)

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/expenses/summary?from=2026-10-01&to=2026-10-31')
    const headers = init?.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer token-de-prueba')
  })
})