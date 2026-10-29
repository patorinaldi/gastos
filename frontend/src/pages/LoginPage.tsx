import { useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation } from 'react-router'
import { login } from '../api/auth.ts'
import { ApiError, errorMessage } from '../api/client.ts'
import { ResendVerification } from '../components/auth/ResendVerification.tsx'
import { Button } from '../components/ui/Button.tsx'
import { Notice } from '../components/ui/Notice.tsx'
import { TextField } from '../components/ui/TextField.tsx'
import { saveSession, useSession } from '../session.ts'
import { validateEmail, validatePassword } from '../validation.ts'
import styles from './AuthPages.module.css'

interface Errors {
  email?: string
  password?: string
}

/**
 * Inicio de sesión (RF-33, CU-02). Con la sesión iniciada vuelve a la pantalla que la pidió, o al
 * tablero.
 */
export function LoginPage() {
  const session = useSession()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  // El correo con el que el servidor respondió "sin verificar": se ofrece reenviarle el enlace.
  const [unverifiedEmail, setUnverifiedEmail] = useState<string | null>(null)
  const emailInput = useRef<HTMLInputElement>(null)
  const passwordInput = useRef<HTMLInputElement>(null)

  // Guardar la sesión vuelve a dibujar la pantalla, y este Navigate es el que la deja: no hace
  // falta navegar a mano después del login.
  if (session) {
    return <Navigate to={returnPath(location.state)} replace />
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const found: Errors = { email: validateEmail(email), password: validatePassword(password) }
    setErrors(found)
    setFormError(null)
    setUnverifiedEmail(null)
    if (found.email || found.password) {
      const firstInvalid = found.email ? emailInput : passwordInput
      firstInvalid.current?.focus()
      return
    }

    setSubmitting(true)
    const trimmedEmail = email.trim()
    try {
      saveSession(await login({ email: trimmedEmail, password }))
    } catch (error) {
      setFormError(errorMessage(error, 'Revisá el correo y la contraseña e intentá de nuevo.'))
      // El 403 del login es un correo sin verificar. El de un hogar archivado también es 403, pero no
      // debería ocurrir (HouseholdArchivedException), y ahí el reenvío no manda nada: no hace daño.
      if (error instanceof ApiError && error.status === 403) {
        setUnverifiedEmail(trimmedEmail)
      }
      setSubmitting(false)
    }
  }

  return (
    <>
      <header className={styles.header}>
        <h1 className={styles.title}>Iniciar sesión</h1>
        <p className={styles.lede}>Entrá con el correo y la contraseña de tu cuenta.</p>
      </header>

      <form className={styles.form} onSubmit={submit} noValidate>
        {formError && <Notice tone="danger">{formError}</Notice>}
        <TextField
          ref={emailInput}
          label="Correo"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={errors.email}
        />
        <TextField
          ref={passwordInput}
          label="Contraseña"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={errors.password}
        />
        <Button type="submit" block disabled={submitting}>
          {submitting ? 'Ingresando…' : 'Iniciar sesión'}
        </Button>
      </form>

      {unverifiedEmail !== null && <ResendVerification email={unverifiedEmail} />}

      <p className={styles.footer}>
        {/* Lleva el correo escrito, para no pedirlo dos veces. */}
        <Link to="/forgot-password" state={{ email: email.trim() }} className={styles.link}>
          ¿Olvidaste tu contraseña?
        </Link>
      </p>

      <p className={styles.footer}>
        ¿No tenés cuenta?{' '}
        <Link to="/register" className={styles.link}>
          Creá una
        </Link>
      </p>
    </>
  )
}

/**
 * A dónde volver: la ruta que guardó RequireSession al mandar al login. Solo se acepta una ruta de
 * la propia app; cualquier otra cosa lleva al tablero.
 */
function returnPath(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'from' in state && typeof state.from === 'string') {
    const from = state.from
    if (from.startsWith('/') && !from.startsWith('//')) {
      return from
    }
  }
  return '/'
}
