import { useRef, useState, type FormEvent } from 'react'
import { flushSync } from 'react-dom'
import { ApiError, errorMessage } from '../../api/client.ts'
import { issueMachineToken, listMachineTokens, revokeMachineToken } from '../../api/machineTokens.ts'
import { API_URL } from '../../config.ts'
import { formatInstant } from '../../format.ts'
import { useRequest } from '../../hooks/useRequest.ts'
import type { CreatedMachineTokenResponse, MachineTokenResponse } from '../../types/api.ts'
import { validatePassword, validateTokenName } from '../../validation.ts'
import { EmptyState, ErrorState, LoadingState } from '../states/States.tsx'
import { Button } from '../ui/Button.tsx'
import { Notice } from '../ui/Notice.tsx'
import { TextField } from '../ui/TextField.tsx'
import styles from './MachineTokens.module.css'

type Copy = 'idle' | 'copied' | 'failed'

/**
 * Los tokens de las automatizaciones (RF-07, HU-17): emitir uno para un atajo del teléfono que carga
 * gastos sin abrir la app (M6), ver los que hay y revocarlos.
 *
 * El token en claro llega solo al emitirlo: vive en el estado de este componente y en ningún otro
 * lado, como el código de invitación. Emitirlo pide la contraseña, porque no vence y carga gastos a
 * nombre de quien lo emite. Cada persona ve solo los suyos, del hogar en el que está: al cambiarse de
 * hogar, el servidor revoca los del anterior.
 */
export function MachineTokens() {
  // Cambia al emitir o revocar, y con ella se vuelve a pedir la lista.
  const [version, setVersion] = useState(0)
  const tokens = useRequest(`machine-tokens#${version}`, listMachineTokens, 'No pudimos cargar tus tokens.')
  const [issued, setIssued] = useState<CreatedMachineTokenResponse | null>(null)

  const refresh = () => setVersion((v) => v + 1)

  return (
    <div className={styles.tokens}>
      <p className={styles.lede}>
        Un token deja que una automatización de tu teléfono, como un atajo que lee la notificación del banco,
        cargue gastos a tu nombre sin abrir la app.
      </p>

      {/* Se anuncia al aparecer: quien usa lector de pantalla se entera del token sin buscarlo. */}
      <div role="status">{issued && <IssuedToken token={issued} onDone={() => setIssued(null)} />}</div>

      {!issued && (
        <IssueForm
          onIssued={(token) => {
            setIssued(token)
            refresh()
          }}
        />
      )}

      {tokens.kind === 'loading' ? (
        <LoadingState label="Cargando tus tokens…" />
      ) : tokens.kind === 'failed' ? (
        <ErrorState message={tokens.message} onRetry={tokens.retry} />
      ) : tokens.data.length === 0 ? (
        <EmptyState
          title="Todavía no emitiste ningún token"
          description="Cuando emitas uno, aparece acá con la fecha de su último uso, y lo podés revocar."
        />
      ) : (
        <ul className={styles.list} aria-label="Tus tokens">
          {tokens.data.map((token) => (
            <TokenRow key={token.id} token={token} onRevoked={refresh} />
          ))}
        </ul>
      )}
    </div>
  )
}

interface IssueFormProps {
  onIssued: (token: CreatedMachineTokenResponse) => void
}

function IssueForm({ onIssued }: IssueFormProps) {
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ name?: string; password?: string }>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const nameInput = useRef<HTMLInputElement>(null)
  const passwordInput = useRef<HTMLInputElement>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const found = { name: validateTokenName(name), password: validatePassword(password) }
    setErrors(found)
    setFormError(null)
    if (found.name || found.password) {
      ;(found.name ? nameInput : passwordInput).current?.focus()
      return
    }

    setSubmitting(true)
    try {
      onIssued(await issueMachineToken(name.trim(), password))
    } catch (error) {
      // La contraseña incorrecta es un error del campo. El tope de vigentes (409) y el de pedidos
      // (429) son del formulario, con el mensaje del servidor, que dice qué hacer.
      if (error instanceof ApiError && error.status === 403) {
        setErrors({ password: errorMessage(error, 'La contraseña no es correcta') })
        passwordInput.current?.focus()
      } else {
        setFormError(errorMessage(error, 'No pudimos emitir el token. Intentá de nuevo.'))
      }
    } finally {
      // La contraseña no queda en el formulario: ni después de un error, ni para emitir otro.
      setPassword('')
      setSubmitting(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      {formError && <Notice tone="danger">{formError}</Notice>}
      <TextField
        ref={nameInput}
        label="Nombre del token"
        hint="Para reconocerlo después, por ejemplo «Atajo del teléfono»."
        autoComplete="off"
        maxLength={100}
        value={name}
        onChange={(event) => setName(event.target.value)}
        error={errors.name}
      />
      <TextField
        ref={passwordInput}
        label="Tu contraseña"
        hint="Confirma que sos vos: el token no vence."
        type="password"
        autoComplete="current-password"
        value={password}
        onChange={(event) => setPassword(event.target.value)}
        error={errors.password}
      />
      <div className={styles.actions}>
        <Button type="submit" disabled={submitting}>
          {submitting ? 'Emitiendo…' : 'Emitir token'}
        </Button>
      </div>
    </form>
  )
}

interface IssuedTokenProps {
  token: CreatedMachineTokenResponse
  onDone: () => void
}

/** El token recién emitido, una sola vez, con lo que hace falta para configurar el atajo. */
function IssuedToken({ token, onDone }: IssuedTokenProps) {
  const [copy, setCopy] = useState<Copy>('idle')

  // Sin HTTPS, o si el navegador no da permiso, el portapapeles no está: queda copiarlo a mano, y
  // por eso el token se puede seleccionar entero con un toque.
  const copyToken = async () => {
    try {
      await navigator.clipboard.writeText(token.token)
      setCopy('copied')
    } catch {
      setCopy('failed')
    }
  }

  return (
    <div className={styles.issued}>
      <p className={styles.issuedTitle}>Token «{token.name}»</p>
      <p className={styles.value}>{token.token}</p>
      <p className={styles.warning}>Copialo ahora: por seguridad no lo vas a poder ver de nuevo.</p>
      <dl className={styles.setup}>
        <dt>Dirección</dt>
        <dd>
          <code>POST {API_URL}/api/capture</code>
        </dd>
        <dt>Encabezado</dt>
        <dd>
          <code>Authorization: Token</code> seguido del token
        </dd>
        <dt>Cuerpo</dt>
        <dd>
          <code>{'{"amount": "1.234,56", "merchant": "Carrefour"}'}</code>
        </dd>
      </dl>
      <div className={styles.actions}>
        <Button onClick={copyToken}>Copiar token</Button>
        <Button variant="secondary" onClick={onDone}>
          Listo
        </Button>
        <span className={copy === 'failed' ? styles.copyFailed : styles.copied} aria-live="polite">
          {copy === 'copied' && 'Token copiado.'}
          {copy === 'failed' && 'No pudimos copiarlo. Seleccioná el token y copialo a mano.'}
        </span>
      </div>
    </div>
  )
}

interface TokenRowProps {
  token: MachineTokenResponse
  onRevoked: () => void
}

function TokenRow({ token, onRevoked }: TokenRowProps) {
  const [confirming, setConfirming] = useState(false)
  const [revoking, setRevoking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const confirmButton = useRef<HTMLButtonElement>(null)
  const revokeButton = useRef<HTMLButtonElement>(null)

  // Al pedir la revocación, el foco pasa a la confirmación; al cancelarla, vuelve al botón que la
  // pidió. El botón aparece recién con el estado nuevo: flushSync lo dibuja antes de enfocarlo.
  const confirm = (value: boolean) => {
    flushSync(() => setConfirming(value))
    ;(value ? confirmButton : revokeButton).current?.focus()
  }

  const revoke = async () => {
    setRevoking(true)
    setError(null)
    try {
      await revokeMachineToken(token.id)
      onRevoked()
    } catch (failure) {
      setError(errorMessage(failure, 'No pudimos revocar el token. Intentá de nuevo.'))
      setRevoking(false)
    }
  }

  const revoked = token.revokedAt !== null

  return (
    <li className={revoked ? `${styles.row} ${styles.revoked}` : styles.row}>
      <div className={styles.rowInfo}>
        <p className={styles.rowName}>{token.name}</p>
        <p className={styles.rowMeta}>
          Emitido el {formatInstant(token.createdAt)} ·{' '}
          {token.lastUsedAt ? `último uso el ${formatInstant(token.lastUsedAt)}` : 'nunca usado'}
        </p>
        {token.revokedAt && <p className={styles.rowMeta}>Revocado el {formatInstant(token.revokedAt)}</p>}
      </div>

      {!revoked && (
        <div className={styles.rowActions}>
          {error && <Notice tone="danger">{error}</Notice>}
          {confirming ? (
            <>
              <p className={styles.confirmText}>Las capturas con este token van a dejar de funcionar.</p>
              <div className={styles.actions}>
                <Button ref={confirmButton} onClick={revoke} disabled={revoking}>
                  {revoking ? 'Revocando…' : 'Sí, revocar'}
                </Button>
                <Button variant="secondary" onClick={() => confirm(false)} disabled={revoking}>
                  Cancelar
                </Button>
              </div>
            </>
          ) : (
            <Button ref={revokeButton} variant="secondary" onClick={() => confirm(true)}>
              Revocar
            </Button>
          )}
        </div>
      )}
    </li>
  )
}
