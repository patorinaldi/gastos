import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { resetPassword, tokenFromHash } from '../api/auth.ts'
import { ApiError, errorMessage } from '../api/client.ts'
import { Button, ButtonLink } from '../components/ui/Button.tsx'
import { Notice } from '../components/ui/Notice.tsx'
import { TextField } from '../components/ui/TextField.tsx'
import { clearSession } from '../session.ts'
import { validateNewPassword } from '../validation.ts'
import styles from './AuthPages.module.css'

type Status =
  | { kind: 'form' }
  | { kind: 'done' }
  /** El enlace no sirve: falta el token, venció o ya se usó (RN-03). Reintentar no cambia nada. */
  | { kind: 'invalid' }

/**
 * Elegir una contraseña nueva con el enlace del correo (RF-06, CU-08, HU-04): `/reset-password#token=...`.
 * Se llega sin sesión.
 *
 * El token se lee al abrir la pantalla y se borra de la dirección, como en la confirmación de
 * correo, pero se canjea recién al enviar el formulario. Un enlace vencido o usado se descubre en
 * ese momento (410) y la pantalla ofrece pedir otro.
 */
export function ResetPasswordPage() {
  const location = useLocation()
  const navigate = useNavigate()
  // El token se guarda al leerlo, porque enseguida se borra de la dirección.
  const hashToken = tokenFromHash(location.hash)
  const [token, setToken] = useState(hashToken)
  const [status, setStatus] = useState<Status>(hashToken ? { kind: 'form' } : { kind: 'invalid' })
  const [password, setPassword] = useState('')
  const [fieldError, setFieldError] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const passwordInput = useRef<HTMLInputElement>(null)
  const title = useRef<HTMLHeadingElement>(null)

  // Abrir otro enlace en la misma pestaña solo cambia el fragmento: la pantalla sigue montada y
  // vuelve a empezar con el token nuevo.
  if (hashToken && hashToken !== token) {
    setToken(hashToken)
    setStatus({ kind: 'form' })
  }

  // El token sale de la barra de direcciones y del historial: un enlace de un solo uso no tiene que
  // quedar a la vista ni volver con "atrás".
  useEffect(() => {
    if (location.hash) {
      navigate({ pathname: location.pathname, search: location.search }, { replace: true })
    }
  }, [location.hash, location.pathname, location.search, navigate])

  // El formulario desaparece al terminar: el foco pasa al título nuevo para que se anuncie.
  useEffect(() => {
    if (status.kind !== 'form') {
      title.current?.focus()
    }
  }, [status.kind])

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const invalid = validateNewPassword(password)
    setFieldError(invalid)
    setFormError(null)
    if (invalid || !token) {
      passwordInput.current?.focus()
      return
    }

    setSubmitting(true)
    try {
      await resetPassword(token, password)
      // El servidor cerró todas las sesiones de la cuenta (RNF-29). Si en este navegador había una,
      // ya no sirve: se descarta acá en lugar de esperar al primer 401.
      clearSession()
      setStatus({ kind: 'done' })
    } catch (error) {
      if (error instanceof ApiError && error.status === 410) {
        setStatus({ kind: 'invalid' })
      } else {
        setFormError(errorMessage(error, 'Revisá la contraseña e intentá de nuevo.'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  switch (status.kind) {
    case 'done':
      return (
        <>
          <header className={styles.header}>
            <h1 ref={title} tabIndex={-1} className={styles.title}>
              Contraseña cambiada
            </h1>
            <p className={styles.lede}>
              Ya podés iniciar sesión con la contraseña nueva. Por seguridad, se cerraron las sesiones
              abiertas en otros dispositivos.
            </p>
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
              Puede que haya vencido, que ya se haya usado o que no se haya copiado entero. Pedí uno
              nuevo con el correo de tu cuenta.
            </p>
          </header>
          <ButtonLink to="/forgot-password" block>
            Pedir un enlace nuevo
          </ButtonLink>
          <p className={styles.footer}>
            ¿Te acordaste?{' '}
            <Link to="/login" className={styles.link}>
              Iniciá sesión
            </Link>
          </p>
        </>
      )
    case 'form':
      return (
        <>
          <header className={styles.header}>
            <h1 className={styles.title}>Elegí una contraseña nueva</h1>
            <p className={styles.lede}>Va a reemplazar a la anterior en todos tus dispositivos.</p>
          </header>

          <form className={styles.form} onSubmit={submit} noValidate>
            {formError && <Notice tone="danger">{formError}</Notice>}
            <TextField
              ref={passwordInput}
              label="Contraseña nueva"
              type="password"
              autoComplete="new-password"
              hint="Al menos 8 caracteres."
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              error={fieldError}
            />
            <Button type="submit" block disabled={submitting}>
              {submitting ? 'Guardando…' : 'Guardar la contraseña'}
            </Button>
          </form>
        </>
      )
  }
}
