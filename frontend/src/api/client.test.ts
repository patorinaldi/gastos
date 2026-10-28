import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryStorage } from '../test-support.ts'
import type { ApiError } from './client.ts'

const FUTURE = '2099-01-01T00:00:00Z'

const fetchMock = vi.fn<typeof fetch>()

// El cliente y la sesión comparten estado: se cargan juntos y de nuevo en cada prueba.
async function load() {
  vi.resetModules()
  const session = await import('../session.ts')
  const client = await import('./client.ts')
  return { ...session, ...client }
}

function problem(status: number, detail: string, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify({ status, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json', ...headers },
  })
}

/** El error con que falló la petición; si no falló, la prueba falla. */
async function rejection(request: Promise<unknown>): Promise<ApiError> {
  try {
    await request
  } catch (error) {
    return error as ApiError
  }
  throw new Error('La petición no falló')
}

function sentHeaders(call = 0): Record<string, string> {
  return fetchMock.mock.calls[call][1]?.headers as Record<string, string>
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('localStorage', new MemoryStorage())
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('apiRequest', () => {
  it('manda el cuerpo como JSON y devuelve la respuesta', async () => {
    const { apiRequest } = await load()
    fetchMock.mockResolvedValue(Response.json({ token: 't', expiresAt: FUTURE }))

    const result = await apiRequest('/api/auth/login', { method: 'POST', body: { email: 'a@b' }, auth: false })

    expect(result).toEqual({ token: 't', expiresAt: FUTURE })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/auth/login')
    expect(init?.method).toBe('POST')
    expect(init?.body).toBe('{"email":"a@b"}')
    expect(sentHeaders()['Content-Type']).toBe('application/json')
  })

  it('una respuesta sin cuerpo devuelve undefined', async () => {
    const { apiRequest } = await load()
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))
    await expect(apiRequest('/api/auth/verify', { method: 'POST', body: {}, auth: false })).resolves.toBeUndefined()
  })

  it('manda el token de sesión, salvo en las rutas públicas', async () => {
    const { apiRequest, saveSession } = await load()
    saveSession({ token: 'token-de-prueba', expiresAt: FUTURE })
    fetchMock.mockImplementation(async () => Response.json({}))

    await apiRequest('/api/auth/me')
    await apiRequest('/api/auth/login', { method: 'POST', body: {}, auth: false })

    expect(sentHeaders(0).Authorization).toBe('Bearer token-de-prueba')
    expect(sentHeaders(1).Authorization).toBeUndefined()
  })

  it('un error trae el estado y el detalle del problem+json', async () => {
    const { apiRequest, ApiError: ApiErrorClass } = await load()
    fetchMock.mockResolvedValue(problem(409, 'Ya existe una cuenta con ese correo.'))

    const error = await rejection(apiRequest('/api/auth/register', { method: 'POST', body: {}, auth: false }))

    expect(error).toBeInstanceOf(ApiErrorClass)
    expect(error.status).toBe(409)
    expect(error.detail).toBe('Ya existe una cuenta con ese correo.')
  })

  it('lee Retry-After de un 429', async () => {
    const { apiRequest } = await load()
    fetchMock.mockResolvedValue(problem(429, 'Demasiados intentos', { 'Retry-After': '540' }))
    const error = await rejection(apiRequest('/api/auth/login', { method: 'POST', auth: false }))
    expect(error.retryAfter).toBe(540)
  })

  it('sin respuesta del servidor, el estado es 0', async () => {
    const { apiRequest } = await load()
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))
    const error = await rejection(apiRequest('/api/auth/me'))
    expect(error.status).toBe(0)
    expect(error.detail).toBeNull()
  })

  it('un error sin cuerpo JSON no trae detalle', async () => {
    const { apiRequest } = await load()
    fetchMock.mockResolvedValue(new Response('<html>Bad Gateway</html>', { status: 502 }))
    const error = await rejection(apiRequest('/api/auth/me'))
    expect(error.status).toBe(502)
    expect(error.detail).toBeNull()
  })

  it('un 401 con token cierra la sesión', async () => {
    const { apiRequest, getSession, saveSession } = await load()
    saveSession({ token: 'token-de-prueba', expiresAt: FUTURE })
    fetchMock.mockResolvedValue(problem(401, 'Iniciá sesión para continuar.'))

    await apiRequest('/api/auth/me').catch(() => undefined)

    expect(getSession()).toBeNull()
  })

  it('un 401 del login no toca la sesión', async () => {
    const { apiRequest, getSession, saveSession } = await load()
    saveSession({ token: 'token-de-prueba', expiresAt: FUTURE })
    fetchMock.mockResolvedValue(problem(401, 'Correo o contraseña incorrectos'))

    await apiRequest('/api/auth/login', { method: 'POST', body: {}, auth: false }).catch(() => undefined)

    expect(getSession()?.token).toBe('token-de-prueba')
  })
})

describe('errorMessage', () => {
  it('usa el detalle que el servidor escribió para el usuario', async () => {
    const { ApiError, errorMessage } = await load()
    expect(errorMessage(new ApiError(403, 'El correo todavía no fue verificado.'), 'otro')).toBe(
      'El correo todavía no fue verificado.',
    )
  })

  it('no muestra el detalle técnico de un 400', async () => {
    const { ApiError, errorMessage } = await load()
    expect(errorMessage(new ApiError(400, 'Invalid request content.'), 'Revisá los datos.')).toBe('Revisá los datos.')
  })

  it('dice cuánto esperar ante un 429', async () => {
    const { ApiError, errorMessage } = await load()
    expect(errorMessage(new ApiError(429, 'x', 540), '')).toBe('Hiciste demasiados intentos. Esperá 9 minutos y volvé a intentar.')
    expect(errorMessage(new ApiError(429, 'x', 30), '')).toBe('Hiciste demasiados intentos. Esperá un minuto y volvé a intentar.')
    expect(errorMessage(new ApiError(429, 'x'), '')).toBe('Hiciste demasiados intentos. Esperá unos minutos y volvé a intentar.')
  })

  it('muestra el detalle del 503 de la API, pero no el de un proxy sin detalle', async () => {
    const { ApiError, errorMessage } = await load()
    expect(errorMessage(new ApiError(503, 'Por ahora no podemos enviar correos.'), '')).toBe(
      'Por ahora no podemos enviar correos.',
    )
    expect(errorMessage(new ApiError(503), '')).toMatch(/de nuestro lado/)
  })

  it('distingue la falta de conexión de un error del servidor', async () => {
    const { ApiError, errorMessage } = await load()
    expect(errorMessage(new ApiError(0), '')).toMatch(/Revisá tu conexión/)
    expect(errorMessage(new ApiError(500, 'NullPointerException'), '')).toMatch(/de nuestro lado/)
  })
})
