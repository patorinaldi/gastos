import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, errorMessage } from '../../api/client.ts'
import { previewInvitation, redeemInvitation } from '../../api/household.ts'
import type { InvitationPreviewResponse, RedeemInvitationResponse } from '../../types/api.ts'
import { validateInvitationCode } from '../../validation.ts'
import { Button } from '../ui/Button.tsx'
import { Notice } from '../ui/Notice.tsx'
import { TextField } from '../ui/TextField.tsx'
import styles from './JoinHousehold.module.css'

interface JoinHouseholdProps {
  /** El canje salió bien: la persona ya integra el hogar nuevo. */
  onJoined: (result: RedeemInvitationResponse) => void
}

type Step = { kind: 'code' } | { kind: 'confirm'; preview: InvitationPreviewResponse }

/**
 * Unirse a otro hogar con un código (RF-09, HU-06, CU-03).
 *
 * Todo usuario ya tiene un hogar (RN-11), así que unirse a otro es cambiarse: sus gastos quedan en el
 * que deja y deja de verlos (RN-18). Por eso va en dos pasos. Primero la vista previa valida el código
 * y dice qué va a pasar, sin aplicar nada; recién con la confirmación se canjea. Desistir no consume
 * el código.
 */
export function JoinHousehold({ onJoined }: JoinHouseholdProps) {
  const [step, setStep] = useState<Step>({ kind: 'code' })
  const [code, setCode] = useState('')
  const [fieldError, setFieldError] = useState<string | undefined>()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  // Al volver de la confirmación, el campo recibe el foco; al abrir la pantalla, no.
  const [returned, setReturned] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const confirmTitle = useRef<HTMLHeadingElement>(null)

  // El formulario desaparece al pasar a la confirmación: el foco va a su título, para que se anuncie.
  useEffect(() => {
    if (step.kind === 'confirm') {
      confirmTitle.current?.focus()
    }
  }, [step.kind])

  const backToCode = (message: string | null) => {
    setStep({ kind: 'code' })
    setError(message)
    setReturned(true)
  }

  const check = async (event: FormEvent) => {
    event.preventDefault()
    const invalid = validateInvitationCode(code)
    setFieldError(invalid)
    setError(null)
    if (invalid) {
      input.current?.focus()
      return
    }

    setSubmitting(true)
    try {
      setStep({ kind: 'confirm', preview: await previewInvitation(code.trim()) })
    } catch (e) {
      // El 410 (inexistente, vencido, canjeado) y el 409 (del hogar propio) traen su mensaje, y el 429
      // dice cuánto esperar (RNF-30).
      setError(errorMessage(e, 'Revisá el código e intentá de nuevo.'))
      input.current?.focus()
    } finally {
      setSubmitting(false)
    }
  }

  const confirm = async () => {
    setSubmitting(true)
    setError(null)
    try {
      onJoined(await redeemInvitation(code.trim()))
    } catch (e) {
      const message = errorMessage(e, 'No pudimos completar el cambio de hogar. Intentá de nuevo.')
      // Si el código dejó de servir entre la vista previa y el canje (venció, otra persona lo usó), la
      // confirmación ya no tiene sentido: se vuelve al código. Cualquier otro error se puede reintentar.
      if (e instanceof ApiError && (e.status === 409 || e.status === 410)) {
        backToCode(message)
      } else {
        setError(message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (step.kind === 'confirm') {
    const { householdName, currentHouseholdWillBeArchived, machineTokensToRevoke } = step.preview
    return (
      <div className={styles.confirm}>
        <h3 ref={confirmTitle} tabIndex={-1} className={styles.confirmTitle}>
          ¿Unirte a «{householdName}»?
        </h3>
        <ul className={styles.consequences}>
          <li>Dejás tu hogar actual. Los gastos que cargaste quedan ahí y vas a dejar de verlos.</li>
          {currentHouseholdWillBeArchived ? (
            <li>
              Sos su único integrante, así que <strong>queda archivado</strong>: sus datos se conservan, pero nadie
              va a poder verlos.
            </li>
          ) : (
            <li>Sigue activo para los demás integrantes, que van a seguir viendo tus gastos con tu nombre.</li>
          )}
          {machineTokensToRevoke > 0 && (
            <li>
              {machineTokensToRevoke === 1
                ? 'Se revoca el token de tu automatización conectada a este hogar.'
                : `Se revocan los ${machineTokensToRevoke} tokens de tus automatizaciones conectadas a este hogar.`}
            </li>
          )}
          <li>Vas a ver los gastos que ya cargaron en «{householdName}».</li>
        </ul>
        {error && <Notice tone="danger">{error}</Notice>}
        <div className={styles.actions}>
          <Button onClick={confirm} disabled={submitting}>
            {submitting ? 'Cambiando de hogar…' : 'Confirmar y unirme'}
          </Button>
          <Button variant="secondary" onClick={() => backToCode(null)} disabled={submitting}>
            Cancelar
          </Button>
        </div>
      </div>
    )
  }

  return (
    <form className={styles.form} onSubmit={check} noValidate>
      <p className={styles.lede}>
        Si alguien te pasó un código, ingresalo para sumarte a su hogar. Antes de confirmar te mostramos qué pasa
        con tus gastos.
      </p>
      {error && <Notice tone="danger">{error}</Notice>}
      <TextField
        ref={input}
        label="Código de invitación"
        placeholder="GST-ABCD-EFG"
        autoComplete="off"
        autoCapitalize="characters"
        spellCheck={false}
        autoFocus={returned}
        value={code}
        onChange={(event) => setCode(event.target.value)}
        error={fieldError}
      />
      <div className={styles.actions}>
        <Button type="submit" variant="secondary" disabled={submitting}>
          {submitting ? 'Verificando…' : 'Continuar'}
        </Button>
      </div>
    </form>
  )
}
