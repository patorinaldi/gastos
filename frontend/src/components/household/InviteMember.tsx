import { useState } from 'react'
import { errorMessage } from '../../api/client.ts'
import { createInvitation } from '../../api/household.ts'
import { formatInstant } from '../../format.ts'
import type { InvitationResponse } from '../../types/api.ts'
import { Button } from '../ui/Button.tsx'
import { Notice } from '../ui/Notice.tsx'
import styles from './InviteMember.module.css'

type Copy = 'idle' | 'copied' | 'failed'

/**
 * Generar un código de invitación (RF-08, HU-05).
 *
 * El código en claro llega solo en la respuesta: la base guarda su hash y no se puede volver a pedir.
 * Vive en el estado de este componente y en ningún otro lado: ni en la dirección, ni en el
 * almacenamiento del navegador, ni en la consola. Al salir de la pantalla se pierde, y si hace falta
 * se genera otro.
 */
export function InviteMember() {
  const [invitation, setInvitation] = useState<InvitationResponse | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [copy, setCopy] = useState<Copy>('idle')

  const generate = async () => {
    setSubmitting(true)
    setError(null)
    setCopy('idle')
    try {
      setInvitation(await createInvitation())
    } catch (e) {
      // El 409 del tope de invitaciones vigentes (RNF-30) trae su propio mensaje.
      setError(errorMessage(e, 'No pudimos generar el código. Intentá de nuevo.'))
    } finally {
      setSubmitting(false)
    }
  }

  // Sin HTTPS, o si el navegador no da permiso, el portapapeles no está: queda copiarlo a mano, y por
  // eso el código se puede seleccionar entero con un toque.
  const copyCode = async (code: string) => {
    try {
      await navigator.clipboard.writeText(code)
      setCopy('copied')
    } catch {
      setCopy('failed')
    }
  }

  return (
    <div className={styles.invite}>
      <p className={styles.lede}>
        Generá un código y pasáselo a quien convive con vos. Sirve para una sola persona y vence a los 7 días.
      </p>

      {error && <Notice tone="danger">{error}</Notice>}

      {/* Se anuncia al aparecer: quien usa lector de pantalla se entera del código sin buscarlo. */}
      <div role="status">
        {invitation && (
          <div className={styles.issued}>
            <p className={styles.code}>{invitation.code}</p>
            <p className={styles.expiry}>
              Vence el {formatInstant(invitation.expiresAt)}. Copialo ahora: por seguridad no lo vas a poder
              ver de nuevo.
            </p>
          </div>
        )}
      </div>

      <div className={styles.actions}>
        {invitation ? (
          <>
            <Button onClick={() => copyCode(invitation.code)}>Copiar código</Button>
            <Button variant="secondary" onClick={generate} disabled={submitting}>
              {submitting ? 'Generando…' : 'Generar otro'}
            </Button>
          </>
        ) : (
          <Button onClick={generate} disabled={submitting}>
            {submitting ? 'Generando…' : 'Generar código'}
          </Button>
        )}
        {/* Siempre en la página, vacío hasta que se copia: el lector de pantalla anuncia el texto al
            aparecer. */}
        <span className={copy === 'failed' ? styles.copyFailed : styles.copied} aria-live="polite">
          {copy === 'copied' && 'Código copiado.'}
          {copy === 'failed' && 'No pudimos copiarlo. Seleccioná el código y copialo a mano.'}
        </span>
      </div>
    </div>
  )
}
