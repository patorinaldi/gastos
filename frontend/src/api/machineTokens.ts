import type { CreateMachineTokenRequest, CreatedMachineTokenResponse, MachineTokenResponse } from '../types/api.ts'
import { apiRequest } from './client.ts'

/**
 * M1, tokens de cliente máquina (RF-07, HU-17). Piden sesión: cada persona emite, ve y revoca solo
 * los suyos, del hogar en el que está.
 */

/**
 * Emite un token para una automatización. Pide la contraseña actual, porque el token no vence y
 * carga gastos a nombre de quien lo emite. El token en claro llega solo en esta respuesta.
 */
export function issueMachineToken(name: string, password: string) {
  return apiRequest<CreatedMachineTokenResponse>('/api/auth/machine-tokens', {
    method: 'POST',
    body: { name, password } satisfies CreateMachineTokenRequest,
  })
}

/** Los tokens vigentes y los revocados en los últimos 30 días, del más nuevo al más viejo. */
export function listMachineTokens() {
  return apiRequest<MachineTokenResponse[]>('/api/auth/machine-tokens')
}

/** Revocarlo corta las capturas que lleguen con él. La sesión web sigue activa. */
export function revokeMachineToken(id: string) {
  return apiRequest<void>(`/api/auth/machine-tokens/${encodeURIComponent(id)}`, { method: 'DELETE' })
}
