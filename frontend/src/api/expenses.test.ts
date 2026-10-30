import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryStorage } from '../test-support.ts'

const fetchMock = vi.fn<typeof fetch>()

const ID = '33333333-3333-3333-3333-333333333333'

// El cliente lee el token de la sesión: se cargan juntos y de nuevo en cada prueba.
async function load() {
  vi.resetModules()
  const session = await import('../session.ts')
  const expenses = await import('./expenses.ts')
  const categories = await import('./categories.ts')
  session.saveSession({ token: 'token-de-prueba', expiresAt: '2099-01-01T00:00:00Z' })
  return { ...expenses, ...categories }
}

/** La última petición: dirección, método, cuerpo y token. */
function lastRequest() {
  const [url, init] = fetchMock.mock.calls.at(-1)!
  const headers = init?.headers as Record<string, string>
  return {
    url,
    method: init?.method,
    body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
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

describe('gastos', () => {
  it('pide un gasto por su identificador', async () => {
    const { getExpense } = await load()
    fetchMock.mockResolvedValue(Response.json({ id: ID }))

    await expect(getExpense(ID)).resolves.toEqual({ id: ID })
    expect(lastRequest()).toMatchObject({
      url: `http://localhost:8080/api/expenses/${ID}`,
      method: 'GET',
      authorization: 'Bearer token-de-prueba',
    })
  })

  it('codifica el identificador, que puede venir de la dirección', async () => {
    const { getExpense } = await load()
    fetchMock.mockResolvedValue(Response.json({}))

    await getExpense('../auth/me')
    expect(lastRequest().url).toBe('http://localhost:8080/api/expenses/..%2Fauth%2Fme')
  })

  it('registra un gasto con el importe como texto', async () => {
    const { createExpense } = await load()
    fetchMock.mockResolvedValue(Response.json({ id: ID }, { status: 201 }))

    await createExpense({ amount: '1234.56', merchant: 'Carrefour', paymentMethod: 'Tarjeta' })
    expect(lastRequest()).toMatchObject({
      url: 'http://localhost:8080/api/expenses',
      method: 'POST',
      body: { amount: '1234.56', merchant: 'Carrefour', paymentMethod: 'Tarjeta' },
      authorization: 'Bearer token-de-prueba',
    })
  })

  it('modifica solo lo que se manda', async () => {
    const { updateExpense } = await load()
    fetchMock.mockResolvedValue(Response.json({ id: ID }))

    await updateExpense(ID, { uncategorized: true })
    expect(lastRequest()).toMatchObject({
      url: `http://localhost:8080/api/expenses/${ID}`,
      method: 'PATCH',
      body: { uncategorized: true },
    })
  })

  it('da de baja un gasto, con la respuesta vacía del 204', async () => {
    const { deleteExpense } = await load()
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await expect(deleteExpense(ID)).resolves.toBeUndefined()
    expect(lastRequest()).toMatchObject({ url: `http://localhost:8080/api/expenses/${ID}`, method: 'DELETE' })
  })

  it('un gasto de otro hogar responde como uno inexistente', async () => {
    const { getExpense } = await load()
    fetchMock.mockResolvedValue(Response.json({ detail: 'No existe ese gasto.' }, { status: 404 }))

    await expect(getExpense(ID)).rejects.toMatchObject({ status: 404, detail: 'No existe ese gasto.' })
  })
})

describe('categorías', () => {
  it('pide las categorías activas', async () => {
    const { listCategories } = await load()
    fetchMock.mockResolvedValue(Response.json([{ id: ID, name: 'Supermercado' }]))

    await expect(listCategories()).resolves.toEqual([{ id: ID, name: 'Supermercado' }])
    expect(lastRequest()).toMatchObject({ url: 'http://localhost:8080/api/categories', method: 'GET' })
  })
})
