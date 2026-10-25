import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { tokenFromHash, verifyEmail } from '../api/auth.ts'
import { ApiError, errorMessage } from '../api/client.ts'
import { ErrorState, LoadingState } from '../components/states/States.tsx'
import { ButtonLink } from '../components/ui/Button.tsx'
import styles from './AuthPages.module.css'

type Status =
  | { kind: 'verifying' }
  | { kind: 'verified' }
  /** El enlace no sirve: falta el token, venció o ya se usó. Reintentar no cambia nada. */
  | { kind: 'invalid' }
  /** No se pudo confirmar por algo pasajero: sin conexión, el servidor, el límite de intentos. */
  | { kind: 'failed'; message: string }

/**
 * Confirmación de correo (RF-33, RF-03). Se llega desde el enlace del correo de alta, sin sesión.
 */
export function VerifyEmailPage() {
  const location = useLocation()
  const navigate = useNavigate()
  // Se lee una sola vez, al entrar: enseguida se borra de la dirección.
  const [token] = useState(() => tokenFromHash(location.hash))
  const [status, setStatus] = useState<Status>(token ? { kind: 'verifying' } : { kind: 'invalid' })
  const [attempt, setAttempt] = useState(0)
  const title = useRef<HTMLHeadingElement>(null)

  // El token sale de la barra de direcciones y del historial: un enlace de un solo uso no tiene que
  // quedar a la vista ni volver con "atrás".
  useEffect(() => {
    if (location.hash) {
      navigate({ pathname: location.pathname, search: location.search }, { replace: true })
    }
  }, [location.hash, location.pathname, location.search, navigate])

  useEffect(() => {
    if (!token) {
      return
    }
    let active = true
    verifyEmail(token).then(
      () => active && setStatus({ kind: 'verified' }),
      (error: unknown) => active && setStatus(failure(error)),
    )
    return () => {
      active = false
    }
  }, [token, attempt])

  // Cada cambio de estado cambia el título: se enfoca para que el lector de pantalla lo anuncie.
  useEffect(() => {
    if (status.kind !== 'verifying') {
      title.current?.focus()
    }
  }, [status.kind])

  const retry = () => {
    setStatus({ kind: 'verifying' })
    setAttempt((n) => n + 1)
  }

  switch (status.kind) {
    case 'verifying':
      return (
        <>
          <h1 className={styles.title}>Confirmando tu correo</h1>
          <LoadingState label="Confirmando…" />
        </>
      )
    case 'verified':
      return (
        <>
          <header className={styles.header}>
            <h1 ref={title} tabIndex={-1} className={styles.title}>
              Correo confirmado
            </h1>
            <p className={styles.lede}>Tu cuenta ya está lista. Iniciá sesión para empezar a cargar gastos.</p>
          </header>
          <ButtonLink to="/login" block>
            Iniciar sesión
          </ButtonLink>
        </>
      )
    case 'invalid':
      return (
        <>
          <header className={styles.header}>
            <h1 ref={title} tabIndex={-1} className={styles.title}>
              El enlace no es válido
            </h1>
            <p className={styles.lede}>
              Puede que haya vencido, que ya se haya usado o que no se haya copiado entero. Si ya
              confirmaste tu correo, iniciá sesión.
            </p>
          </header>
          <ButtonLink to="/login" variant="secondary" block>
            Ir a iniciar sesión
          </ButtonLink>
        </>
      )
    case 'failed':
      return (
        <>
          <h1 ref={title} tabIndex={-1} className={styles.title}>
            No pudimos confirmar tu correo
          </h1>
          <ErrorState message={status.message} onRetry={retry} />
        </>
      )
  }
}

/**
 * Un 4xx es el enlace: el servidor no dice si el token existió, venció o ya se usó (RN-03). El 429
 * es la excepción, porque esperar sí lo arregla.
 */
function failure(error: unknown): Status {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500 && error.status !== 429) {
    return { kind: 'invalid' }
  }
  return { kind: 'failed', message: errorMessage(error, 'Intentá de nuevo en unos minutos.') }
}
