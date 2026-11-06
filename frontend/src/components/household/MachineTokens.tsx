import { useEffect, useRef, useState, type FormEvent } from 'react'
import { flushSync } from 'react-dom'
import { ApiError, errorMessage } from '../../api/client.ts'
import { issueMachineToken, listMachineTokens, revokeMachineToken } from '../../api/machineTokens.ts'
import { formatInstant } from '../../format.ts'
import { useRequest } from '../../hooks/useRequest.ts'
import type { CreatedMachineTokenResponse, MachineTokenResponse } from '../../types/api.ts'
import { validatePassword, validateTokenName } from '../../validation.ts'
import automationImage from '../../assets/atajo/automatizacion.webp'
import tokenPromptImage from '../../assets/atajo/pedir-token.webp'
import { EmptyState, ErrorState, LoadingState } from '../states/States.tsx'
import { Button, ButtonLink } from '../ui/Button.tsx'
import { Notice } from '../ui/Notice.tsx'
import { TextField } from '../ui/TextField.tsx'
import styles from './MachineTokens.module.css'

type Copy = 'idle' | 'copied' | 'failed'

/**
 * El atajo de iPhone (docs/atajo.md). Si el atajo cambia, se comparte de nuevo y cambia este enlace,
 * y quizás las capturas de los pasos.
 */
const SHORTCUT_URL = 'https://www.icloud.com/shortcuts/2d36a066c057443ea1312e72540bea31'

/**
 * Los tokens de las automatizaciones (RF-07, HU-17): emitir uno para un atajo del teléfono que carga
 * gastos sin abrir la app (M6), ver los que hay y revocarlos.
 *
 * La sección es también la guía del atajo de iPhone: cuatro pasos, con el formulario de emisión en
 * el segundo, porque emitir el token es parte de configurarlo. Lo mismo, con más detalle, está en
 * docs/atajo.md.
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

  // Lo que desaparece de la pantalla se lleva el foco, y quedaría en el body. Al cerrar el token
  // emitido, el foco va al formulario que vuelve; al revocar uno, al nombre de ese token en la lista
  // nueva, que dice "Revocado".
  const [focusForm, setFocusForm] = useState(false)
  const [revokedId, setRevokedId] = useState<string | null>(null)

  const refresh = () => setVersion((v) => v + 1)

  return (
    <div className={styles.tokens}>
      <p className={styles.lede}>
        Con el atajo de iPhone, cada pago con Apple Pay se registra solo, sin abrir la app. Necesita iOS 27 o
        posterior.
      </p>

      <ol className={styles.steps}>
        <li className={styles.step}>
          <h3 className={styles.stepTitle}>Instalá el atajo</h3>
          <p className={styles.stepText}>
            Abrilo desde el iPhone y tocá <strong>Agregar atajo</strong>. En la computadora, el enlace solo lo
            muestra.
          </p>
          <div className={styles.actions}>
            <ButtonLink variant="secondary" to={SHORTCUT_URL} target="_blank" rel="noreferrer">
              Instalar el atajo
            </ButtonLink>
          </div>
        </li>

        <li className={styles.step}>
          <h3 className={styles.stepTitle}>Emití un token</h3>
          <p className={styles.stepText}>
            Es la llave con la que el atajo carga gastos a tu nombre. Ponele un nombre que identifique el
            teléfono, y copialo: se muestra una sola vez.
          </p>
          {/* Se anuncia al aparecer: quien usa lector de pantalla se entera del token sin buscarlo. */}
          <div role="status">
            {issued && (
              <IssuedToken
                token={issued}
                onDone={() => {
                  setIssued(null)
                  setFocusForm(true)
                }}
              />
            )}
          </div>
          {!issued && (
            <IssueForm
              focusOnMount={focusForm}
              onIssued={(token) => {
                setIssued(token)
                refresh()
              }}
            />
          )}
        </li>

        <li className={styles.step}>
          <h3 className={styles.stepTitle}>Ejecutalo una vez a mano</h3>
          <p className={styles.stepText}>
            Abrí la app Atajos y tocá <strong>Gastos</strong>. Te pide el token: pegalo y tocá{' '}
            <strong>Listo</strong>. Si sirve, lo guarda en el teléfono y te avisa a quién y a qué hogar se
            conectó. Esta vez no registra ningún gasto.
          </p>
          <figure className={styles.figure}>
            <img
              src={tokenPromptImage}
              width={660}
              height={303}
              loading="lazy"
              alt="El atajo pide el token con el mensaje «Pega tu token de Gastos», un campo de texto y los botones Cancelar y Listo."
            />
          </figure>
        </li>

        <li className={styles.step}>
          <h3 className={styles.stepTitle}>Activá la automatización</h3>
          <p className={styles.stepText}>
            En Atajos, en <strong>Automatización</strong>, aparece «Cuando use un pase o tarjeta de pago de Wallet
            sin contacto», desactivada. Activala: desde ese momento, cada pago con Apple Pay se registra.
          </p>
          <figure className={styles.figure}>
            <img
              src={automationImage}
              width={660}
              height={274}
              loading="lazy"
              alt="La automatización «Cuando use un pase o tarjeta de pago de Wallet sin contacto», del atajo Gastos, con su interruptor apagado."
            />
          </figure>
        </li>
      </ol>

      <details className={styles.details}>
        <summary>Qué significan las notificaciones</summary>
        <dl className={styles.notices}>
          <dt>Gasto registrado</dt>
          <dd>El gasto está en el hogar, con la categoría de tus reglas. Sin categoría, quedó en la bandeja.</dd>
          <dt>No se registró el gasto</dt>
          <dd>La app rechazó el pago, por ejemplo por un importe que no pudo leer. Cargalo a mano.</dd>
          <dt>No se pudo conectar con el servidor</dt>
          <dd>La app no respondió a tiempo. Cargá ese gasto a mano.</dd>
          <dt>Te vuelve a pedir el token</dt>
          <dd>El token ya no sirve, por ejemplo porque lo revocaste. Emití otro y pegalo.</dd>
        </dl>
      </details>

      <details className={styles.details}>
        <summary>Limitaciones</summary>
        <ul className={styles.limits}>
          <li>Solo iPhone, con iOS 27 o posterior.</li>
          <li>Los pagos en otra moneda no se registran: cargalos a mano.</li>
          <li>Si la app estuvo un rato sin uso, el primer pago puede tardar o no registrarse.</li>
        </ul>
      </details>

      <h3 className={styles.listTitle}>Tus tokens</h3>
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
            <TokenRow
              key={token.id}
              token={token}
              focusName={token.id === revokedId}
              onFocused={() => setRevokedId(null)}
              onRevoked={() => {
                setRevokedId(token.id)
                refresh()
              }}
            />
          ))}
        </ul>
      )}
    </div>
  )
}

interface IssueFormProps {
  /** Si al aparecer toma el foco: vuelve después de cerrar un token emitido. */
  focusOnMount: boolean
  onIssued: (token: CreatedMachineTokenResponse) => void
}

function IssueForm({ focusOnMount, onIssued }: IssueFormProps) {
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ name?: string; password?: string }>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const nameInput = useRef<HTMLInputElement>(null)
  const passwordInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (focusOnMount) {
      nameInput.current?.focus()
    }
  }, [focusOnMount])

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

/**
 * El token recién emitido, una sola vez. Solo lo que necesita quien configura el atajo: copiarlo y
 * pegarlo. La dirección, el encabezado y el cuerpo ya los trae el atajo; para otra automatización
 * están en docs/atajo.md.
 */
function IssuedToken({ token, onDone }: IssuedTokenProps) {
  const [copy, setCopy] = useState<Copy>('idle')
  const copyButton = useRef<HTMLButtonElement>(null)

  // El formulario que tenía el foco desaparece al emitir: el foco pasa a lo que hay que hacer ahora.
  useEffect(() => {
    copyButton.current?.focus()
  }, [])

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
      <div className={styles.actions}>
        {/* La confirmación es el botón mismo. Sigue copiando si se vuelve a tocar. */}
        <Button ref={copyButton} onClick={copyToken}>
          {copy === 'copied' ? 'Copiado' : 'Copiar token'}
        </Button>
        <Button variant="secondary" onClick={onDone}>
          Listo
        </Button>
        {/* El cambio de texto de un botón no siempre se anuncia: el lector de pantalla lo oye acá. El
            error sí se ve, porque dice qué hacer. */}
        <span className={copy === 'failed' ? styles.copyFailed : 'visually-hidden'} aria-live="polite">
          {copy === 'copied' && 'Token copiado.'}
          {copy === 'failed' && 'No pudimos copiarlo. Seleccioná el token y copialo a mano.'}
        </span>
      </div>
    </div>
  )
}

interface TokenRowProps {
  token: MachineTokenResponse
  /** Si toma el foco al aparecer: es el que se acaba de revocar. */
  focusName: boolean
  onFocused: () => void
  onRevoked: () => void
}

function TokenRow({ token, focusName, onFocused, onRevoked }: TokenRowProps) {
  const [confirming, setConfirming] = useState(false)
  const [revoking, setRevoking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const confirmButton = useRef<HTMLButtonElement>(null)
  const revokeButton = useRef<HTMLButtonElement>(null)
  const nameRef = useRef<HTMLParagraphElement>(null)

  // Una sola vez: si la lista se vuelve a pedir después, este token no tiene que recuperar el foco.
  useEffect(() => {
    if (focusName) {
      nameRef.current?.focus()
      onFocused()
    }
  }, [focusName, onFocused])

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
        <p ref={nameRef} className={styles.rowName} tabIndex={-1}>
          {token.name}
        </p>
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
