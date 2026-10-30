import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { getHousehold, listMembers } from '../api/household.ts'
import { HouseholdNameForm } from '../components/household/HouseholdNameForm.tsx'
import { InviteMember } from '../components/household/InviteMember.tsx'
import { JoinHousehold } from '../components/household/JoinHousehold.tsx'
import { MemberList } from '../components/household/MemberList.tsx'
import { PageHeader } from '../components/layout/PageHeader.tsx'
import { EmptyState, ErrorState, LoadingState } from '../components/states/States.tsx'
import { ButtonLink } from '../components/ui/Button.tsx'
import { Notice } from '../components/ui/Notice.tsx'
import { useRequest } from '../hooks/useRequest.ts'
import type { HouseholdResponse, RedeemInvitationResponse } from '../types/api.ts'
import styles from './HouseholdPage.module.css'

/**
 * El hogar (RF-38): sus integrantes, invitar a alguien, el nombre, y unirse a otro con un código.
 *
 * Cambiarse de hogar no pide un token nuevo: el servidor resuelve el hogar en cada petición (RN-18),
 * así que alcanza con volver a pedir el hogar y sus integrantes. Cada bloque se monta de nuevo con el
 * hogar nuevo, y se descarta lo que quedaba del anterior, como un código recién generado.
 */
export function HouseholdPage() {
  // Cambia al unirse a otro hogar, y con él se vuelven a pedir el hogar y los integrantes.
  const [version, setVersion] = useState(0)
  const [joined, setJoined] = useState<RedeemInvitationResponse | null>(null)
  // El nombre recién guardado, para el título, sin volver a pedir el hogar.
  const [renamed, setRenamed] = useState<HouseholdResponse | null>(null)
  const joinedNotice = useRef<HTMLDivElement>(null)

  const household = useRequest(`household#${version}`, getHousehold, 'No pudimos cargar el hogar.')
  const members = useRequest(`members#${version}`, listMembers, 'No pudimos cargar los integrantes.')

  // El bloque de canje desaparece al terminar: el foco pasa al aviso, para que se anuncie.
  const loaded = household.kind === 'loaded'
  useEffect(() => {
    if (joined && loaded) {
      joinedNotice.current?.focus()
    }
  }, [joined, loaded])

  const onJoined = (result: RedeemInvitationResponse) => {
    setJoined(result)
    setRenamed(null)
    setVersion((n) => n + 1)
  }

  if (household.kind === 'loading') {
    return (
      <>
        <PageHeader title="Hogar" />
        <LoadingState />
      </>
    )
  }
  if (household.kind === 'failed') {
    return (
      <>
        <PageHeader title="Hogar" />
        <ErrorState message={household.message} onRetry={household.retry} />
      </>
    )
  }

  const current = renamed?.id === household.data.id ? renamed : household.data

  return (
    <>
      <PageHeader eyebrow="Hogar" title={current.name} />

      {joined && (
        <div ref={joinedNotice} tabIndex={-1} className={styles.joined}>
          <Notice
            action={
              <ButtonLink to="/" variant="link">
                Ver el tablero
              </ButtonLink>
            }
          >
            Ahora integrás «{joined.householdName}».{' '}
            {joined.previousHouseholdArchived
              ? 'Tu hogar anterior quedó archivado, con sus datos conservados.'
              : 'Tus gastos anteriores siguen en el hogar que dejaste.'}
          </Notice>
        </div>
      )}

      {/* Cada bloque lleva el hogar como key: al cambiarse de hogar vuelve a empezar. */}
      <div className={styles.layout}>
        <Section title="Integrantes" count={members.kind === 'loaded' ? members.data.length : undefined}>
          {members.kind === 'loading' ? (
            <LoadingState label="Cargando integrantes…" />
          ) : members.kind === 'failed' ? (
            <ErrorState message={members.message} onRetry={members.retry} />
          ) : members.data.length === 0 ? (
            <EmptyState title="No hay integrantes para mostrar" description="Recargá la pantalla para volver a pedirlos." />
          ) : (
            <MemberList members={members.data} />
          )}
        </Section>

        <Section title="Invitar a alguien">
          <InviteMember key={current.id} />
        </Section>

        <Section title="Nombre del hogar">
          <HouseholdNameForm key={current.id} household={current} onRenamed={setRenamed} />
        </Section>

        <Section title="Unirte a otro hogar">
          <JoinHousehold key={current.id} onJoined={onJoined} />
        </Section>
      </div>
    </>
  )
}

interface SectionProps {
  title: string
  /** Cuántos elementos tiene, cuando ya se sabe. */
  count?: number
  children: ReactNode
}

function Section({ title, count, children }: SectionProps) {
  const id = useId()
  return (
    <section className={styles.section} aria-labelledby={id}>
      <h2 id={id} className={styles.title}>
        {title}
        {count !== undefined && <span className={styles.count}> · {count}</span>}
      </h2>
      {children}
    </section>
  )
}
