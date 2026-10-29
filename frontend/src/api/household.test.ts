import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryStorage } from '../test-support.ts'

const fetchMock = vi.fn<typeof fetch>()

// El cliente lee el token de la sesión: se cargan juntos y de nuevo en cada prueba.
async function load() {
  vi.resetModules()
  const session = await import('../session.ts')
  const household = await import('./household.ts')
  session.saveSession({ token: 'token-de-prueba', expiresAt: '2099-01-01T00:00:00Z' })
  return household
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

describe('hogar', () => {
  it('pide el hogar de quien consulta, con el token de sesión', async () => {
    const { getHousehold } = await load()
    const household = { id: 'h1', name: 'Casa', createdAt: '2026-10-01T12:00:00Z' }
    fetchMock.mockResolvedValue(Response.json(household))

    await expect(getHousehold()).resolves.toEqual(household)

    expect(lastCall()).toEqual({
      url: 'http://localhost:8080/api/household',
      method: 'GET',
      body: undefined,
      authorization: 'Bearer token-de-prueba',
    })
  })

  it('renombra el hogar sin mandar su identificador', async () => {
    const { renameHousehold } = await load()
    fetchMock.mockResolvedValue(Response.json({ id: 'h1', name: 'Depto', createdAt: '2026-10-01T12:00:00Z' }))

    await renameHousehold('Depto')

    expect(lastCall()).toMatchObject({ url: 'http://localhost:8080/api/household', method: 'PATCH', body: { name: 'Depto' } })
  })

  it('lista los integrantes', async () => {
    const { listMembers } = await load()
    fetchMock.mockResolvedValue(Response.json([{ id: 'u1', name: 'Ana', email: 'ana@ejemplo.com' }]))

    await expect(listMembers()).resolves.toHaveLength(1)

    expect(lastCall()).toMatchObject({ url: 'http://localhost:8080/api/household/members', method: 'GET' })
  })
})

describe('invitaciones', () => {
  it('genera un código', async () => {
    const { createInvitation } = await load()
    const invitation = { code: 'GST-ABCD-EFG', expiresAt: '2026-11-05T17:30:00Z' }
    fetchMock.mockResolvedValue(Response.json(invitation, { status: 201 }))

    await expect(createInvitation()).resolves.toEqual(invitation)

    expect(lastCall()).toMatchObject({ url: 'http://localhost:8080/api/household/invitations', method: 'POST', body: undefined })
  })

  // El código viaja en el cuerpo y nunca en la ruta, para que no quede en los logs de acceso.
  it('la vista previa manda el código en el cuerpo', async () => {
    const { previewInvitation } = await load()
    fetchMock.mockResolvedValue(
      Response.json({ householdName: 'Casa', currentHouseholdWillBeArchived: true, machineTokensToRevoke: 0 }),
    )

    await previewInvitation('GST-ABCD-EFG')

    const call = lastCall()
    expect(call).toMatchObject({
      url: 'http://localhost:8080/api/household/invitations/preview',
      method: 'POST',
      body: { code: 'GST-ABCD-EFG' },
    })
    expect(call.url).not.toContain('GST')
  })

  it('el canje manda la confirmación explícita', async () => {
    const { redeemInvitation } = await load()
    fetchMock.mockResolvedValue(Response.json({ householdId: 'h2', householdName: 'Casa', previousHouseholdArchived: true }))

    await redeemInvitation('GST-ABCD-EFG')

    expect(lastCall()).toMatchObject({
      url: 'http://localhost:8080/api/household/invitations/redeem',
      method: 'POST',
      body: { code: 'GST-ABCD-EFG', confirm: true },
    })
  })

  it('un código vencido llega como 410 con el mensaje del servidor', async () => {
    const { previewInvitation } = await load()
    const { errorMessage } = await import('./client.ts')
    const detail = 'El código no es válido o ya venció. Pedile a quien te invitó uno nuevo.'
    fetchMock.mockResolvedValue(Response.json({ status: 410, detail }, { status: 410 }))

    const error = await previewInvitation('GST-ABCD-EFG').catch((e: unknown) => e)

    expect(errorMessage(error, 'otro')).toBe(detail)
  })
})
