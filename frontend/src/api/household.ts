import type {
  HouseholdResponse,
  InvitationPreviewRequest,
  InvitationPreviewResponse,
  InvitationResponse,
  MemberResponse,
  RedeemInvitationRequest,
  RedeemInvitationResponse,
  RenameHouseholdRequest,
} from '../types/api.ts'
import { apiRequest } from './client.ts'

/**
 * M2, hogares. Piden sesión, y ninguna lleva el identificador del hogar: el del usuario autenticado
 * es el único al que puede acceder.
 */

export function getHousehold() {
  return apiRequest<HouseholdResponse>('/api/household')
}

/** RF-11. El servidor guarda el nombre sin los espacios de los extremos. */
export function renameHousehold(name: string) {
  return apiRequest<HouseholdResponse>('/api/household', {
    method: 'PATCH',
    body: { name } satisfies RenameHouseholdRequest,
  })
}

/** RF-10. Ordenados por nombre. */
export function listMembers() {
  return apiRequest<MemberResponse[]>('/api/household/members')
}

/**
 * RF-08. El código en claro llega solo en esta respuesta: la base guarda su hash. Como no se puede
 * volver a pedir, la pantalla lo muestra hasta que se va y no lo guarda en ningún lado.
 */
export function createInvitation() {
  return apiRequest<InvitationResponse>('/api/household/invitations', { method: 'POST' })
}

/**
 * RF-09. Valida el código y describe qué pasaría al canjearlo, sin aplicar nada. El código viaja en
 * el cuerpo y nunca en la ruta, para que no quede en los registros de acceso.
 */
export function previewInvitation(code: string) {
  return apiRequest<InvitationPreviewResponse>('/api/household/invitations/preview', {
    method: 'POST',
    body: { code } satisfies InvitationPreviewRequest,
  })
}

/**
 * RF-09, RN-18. Canjea el código y cambia al usuario de hogar. Se llama solo después de que la
 * persona confirmó lo que mostró la vista previa. El token de sesión sigue sirviendo: el servidor
 * resuelve el hogar en cada petición, así que pasa a operar sobre el nuevo.
 */
export function redeemInvitation(code: string) {
  return apiRequest<RedeemInvitationResponse>('/api/household/invitations/redeem', {
    method: 'POST',
    body: { code, confirm: true } satisfies RedeemInvitationRequest,
  })
}
