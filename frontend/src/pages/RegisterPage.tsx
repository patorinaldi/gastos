import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, Navigate } from 'react-router'
import { register } from '../api/auth.ts'
import { ApiError, errorMessage } from '../api/client.ts'
import { Button, ButtonLink } from '../components/ui/Button.tsx'
import { Notice } from '../components/ui/Notice.tsx'
import { TextField } from '../components/ui/TextField.tsx'
import { useSession } from '../session.ts'
import { validateEmail, validateName, validateNewPassword } from '../validation.ts'
import styles from './AuthPages.module.css'

interface Errors {
  name?: string
  email?: string
  password?: string
}

/**
 * Alta de cuenta (RF-33, CU-01). La cuenta nace sin verificar y no puede iniciar sesión hasta que
 * se confirme el correo (RN-02), así que al terminar la pantalla no entra a la app: pide revisar la
 * casilla.
 */
export function RegisterPage() {
  const session = useSession()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null)
  const nameInput = useRef<HTMLInputElement>(null)
  const emailInput = useRef<HTMLInputElement>(null)
  const passwordInput = useRef<HTMLInputElement>(null)
  const doneTitle = useRef<HTMLHeadingElement>(null)

  // El formulario desaparece al terminar: el foco pasa al título nuevo, para que quien usa un
  // lector de pantalla sepa que el alta salió bien y no quede en un botón que ya no existe.
  useEffect(() => {
    if (registeredEmail) {
      doneTitle.current?.focus()
    }
  }, [registeredEmail])

  if (session) {
    return <Navigate to="/" replace />
  }

  if (registeredEmail) {
    return (
      <>
        <header className={styles.header}>
          <h1 ref={doneTitle} tabIndex={-1} className={styles.title}>
            Revisá tu correo
          </h1>
          <p className={styles.lede}>
            Te enviamos un enlace a <strong>{registeredEmail}</strong> para confirmar tu cuenta. Vence en 24
            horas.
          </p>
          <p className={styles.lede}>Cuando lo confirmes, vas a poder iniciar sesión.</p>
        </header>
        <ButtonLink to="/login" variant="secondary" block>
          Ir a iniciar sesión
        </ButtonLink>
      </>
    )
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const found: Errors = {
      name: validateName(name),
      email: validateEmail(email),
      password: validateNewPassword(password),
    }
    setErrors(found)
    setFormError(null)
    if (found.name || found.email || found.password) {
      const firstInvalid = found.name ? nameInput : found.email ? emailInput : passwordInput
      firstInvalid.current?.focus()
      return
    }

    setSubmitting(true)
    try {
      const trimmedEmail = email.trim()
      await register({ name: name.trim(), email: trimmedEmail, password })
      setRegisteredEmail(trimmedEmail)
    } catch (error) {
      // El correo repetido es un error del campo, no del formulario: se marca ahí, con el mensaje
      // del servidor, que sugiere iniciar sesión o recuperar la contraseña.
      if (error instanceof ApiError && error.status === 409) {
        setErrors({ email: errorMessage(error, 'Ya existe una cuenta con ese correo.') })
        emailInput.current?.focus()
      } else {
        setFormError(errorMessage(error, 'Revisá los datos e intentá de nuevo.'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <header className={styles.header}>
        <h1 className={styles.title}>Crear cuenta</h1>
        <p className={styles.lede}>Al crearla se arma también tu hogar, y después podés invitar al resto.</p>
      </header>

      <form className={styles.form} onSubmit={submit} noValidate>
        {formError && <Notice tone="danger">{formError}</Notice>}
        <TextField
          ref={nameInput}
          label="Nombre"
          autoComplete="name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          error={errors.name}
        />
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
          autoComplete="new-password"
          hint="Al menos 8 caracteres."
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={errors.password}
        />
        <Button type="submit" block disabled={submitting}>
          {submitting ? 'Creando cuenta…' : 'Crear cuenta'}
        </Button>
      </form>

      <p className={styles.footer}>
        ¿Ya tenés cuenta?{' '}
        <Link to="/login" className={styles.link}>
          Iniciá sesión
        </Link>
      </p>
    </>
  )
}
