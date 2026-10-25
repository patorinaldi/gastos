import type { LoginRequest, LoginResponse, RegisterRequest, RegisterResponse, VerifyEmailRequest } from '../types/api.ts'
import { apiRequest } from './client.ts'

/** M1, identidad y acceso. Las tres son públicas: no llevan el token de sesión. */

export function register(request: RegisterRequest) {
  return apiRequest<RegisterResponse>('/api/auth/register', { method: 'POST', body: request, auth: false })
}

export function login(request: LoginRequest) {
  return apiRequest<LoginResponse>('/api/auth/login', { method: 'POST', body: request, auth: false })
}

// El token es de un solo uso: un segundo pedido con el mismo token responde que el enlace ya se
// usó. React, en desarrollo, monta dos veces cada pantalla, y la de confirmación lo pediría dos
// veces. Por eso el pedido de cada token se hace una sola vez y los siguientes reciben la misma
// promesa. Si falla, se olvida, para que "Reintentar" vuelva a pedirlo.
const verifications = new Map<string, Promise<void>>()

export function verifyEmail(token: string): Promise<void> {
  let pending = verifications.get(token)
  if (!pending) {
    pending = apiRequest<void>('/api/auth/verify', { method: 'POST', body: { token } satisfies VerifyEmailRequest, auth: false })
    pending.catch(() => verifications.delete(token))
    verifications.set(token, pending)
  }
  return pending
}

/**
 * El token del enlace de confirmación, `/verify-email#token=...`. Viaja en el fragmento, como el
 * del restablecimiento de contraseña: el navegador no lo manda al servidor que sirve la página ni
 * en el header Referer, así que no queda en ningún registro de acceso.
 */
export function tokenFromHash(hash: string): string | null {
  const token = new URLSearchParams(hash.replace(/^#/, '')).get('token')
  return token?.trim() || null
}
