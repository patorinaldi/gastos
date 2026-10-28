import { API_URL } from '../config.ts'
import { clearSessionIfToken, getSession } from '../session.ts'

/*
 * Cliente de la API. Toda pantalla llama al servidor por acá, así el token, los errores y el tiempo
 * de espera se tratan igual en todos lados.
 */

/**
 * Tiempo máximo de espera de una petición. Es largo a propósito: si el backend estaba dormido,
 * arrancarlo tarda entre 15 y 30 segundos (README, "Arranque en frío"), y la primera petición de la
 * mañana tiene que poder esperarlo.
 */
const TIMEOUT_MS = 60_000

/**
 * Error de una petición. `status` es 0 cuando no hubo respuesta: sin conexión, servidor caído o
 * tiempo de espera agotado.
 */
export class ApiError extends Error {
  readonly status: number
  /** El `detail` del problem+json, si vino. El servidor lo escribe para el usuario, en español. */
  readonly detail: string | null
  /** Segundos del header Retry-After de un 429. */
  readonly retryAfter: number | null

  constructor(status: number, detail: string | null = null, retryAfter: number | null = null) {
    super(detail ?? (status === 0 ? 'Sin respuesta del servidor' : `HTTP ${status}`))
    this.name = 'ApiError'
    this.status = status
    this.detail = detail
    this.retryAfter = retryAfter
  }
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'
  body?: unknown
  /**
   * Si manda el token de sesión. Por defecto sí; las rutas públicas (alta, login, verificación) lo
   * desactivan, porque el servidor lo ignora ahí.
   */
  auth?: boolean
}

export async function apiRequest<T>(path: string, { method = 'GET', body, auth = true }: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = auth ? getSession()?.token : undefined
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }

  let response: Response
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: AbortSignal.timeout(TIMEOUT_MS),
    })
  } catch {
    throw new ApiError(0)
  }

  if (!response.ok) {
    // Un 401 con token es una sesión vencida o invalidada: se descarta, y la app vuelve al login.
    if (response.status === 401 && token) {
      clearSessionIfToken(token)
    }
    throw new ApiError(response.status, await problemDetail(response), retryAfter(response))
  }

  return (await readBody(response)) as T
}

async function readBody(response: Response): Promise<unknown> {
  const text = await response.text()
  return text === '' ? undefined : JSON.parse(text)
}

async function problemDetail(response: Response): Promise<string | null> {
  try {
    const body: unknown = await response.json()
    if (typeof body === 'object' && body !== null && 'detail' in body && typeof body.detail === 'string') {
      return body.detail
    }
  } catch {
    // Sin cuerpo, o no era JSON.
  }
  return null
}

function retryAfter(response: Response): number | null {
  const value = Number(response.headers.get('Retry-After'))
  return Number.isInteger(value) && value > 0 ? value : null
}

const NO_CONNECTION = 'No pudimos conectar con el servidor. Revisá tu conexión e intentá de nuevo.'
const SERVER_ERROR = 'Algo falló de nuestro lado. Intentá de nuevo en unos minutos.'

/**
 * El mensaje para mostrarle al usuario (RNF-17).
 *
 * Los rechazos del servidor (401, 403, 409, 410…) traen un `detail` escrito para el usuario, y es
 * el que mejor explica qué pasó: el 403 del login, por ejemplo, puede ser un correo sin verificar o
 * un hogar archivado. Los 400 no: su detalle es el de la validación de Spring, técnico y en inglés,
 * así que se usa el de la pantalla. Lo mismo para la falta de conexión y los errores del servidor,
 * salvo el 503 de la propia API.
 */
export function errorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ApiError)) {
    return SERVER_ERROR
  }
  if (error.status === 0) {
    return NO_CONNECTION
  }
  // El 503 de la API dice qué no está disponible ("no podemos enviar correos"); el de un proxy o
  // de la plataforma no trae detalle y queda con el genérico.
  if (error.status === 503 && error.detail) {
    return error.detail
  }
  if (error.status >= 500) {
    return SERVER_ERROR
  }
  if (error.status === 400) {
    return fallback
  }
  // El detalle del 429 es el mismo para todos los límites y habla de intentos "fallidos", que en el
  // alta no es cierto: ahí cuentan todos. Con Retry-After se puede decir cuánto esperar.
  if (error.status === 429) {
    return `Hiciste demasiados intentos. Esperá ${waitText(error.retryAfter)} y volvé a intentar.`
  }
  return error.detail ?? fallback
}

function waitText(seconds: number | null): string {
  if (seconds === null) {
    return 'unos minutos'
  }
  const minutes = Math.ceil(seconds / 60)
  return minutes <= 1 ? 'un minuto' : `${minutes} minutos`
}
