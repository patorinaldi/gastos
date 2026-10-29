import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { forgotPassword } from '../api/auth.ts'
import { errorMessage } from '../api/client.ts'
import { Button } from '../components/ui/Button.tsx'
import { Notice } from '../components/ui/Notice.tsx'
import { TextField } from '../components/ui/TextField.tsx'
import { validateEmail } from '../validation.ts'
import styles from './AuthPages.module.css'

/**
 * Pedido del enlace para elegir una contraseña nueva (RF-06, CU-08, HU-04). Se llega desde el login,
 * que pasa el correo que se había escrito.
 *
 * El servidor responde lo mismo exista o no la cuenta (CU-08, 1a), así que la confirmación es
 * condicional: "si hay una cuenta con ese correo". Un 429 (RNF-32) o un 503, si el envío de correos
 * no está disponible, se muestran con el mensaje del servidor.
 */
export function ForgotPasswordPage() {
  const location = useLocation()
  const [email, setEmail] = useState(emailFrom(location.state))
  const [fieldError, setFieldError] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [sentTo, setSentTo] = useState<string | null>(null)
  const emailInput = useRef<HTMLInputElement>(null)
  const doneTitle = useRef<HTMLHeadingElement>(null)

  // El formulario desaparece al enviar: el foco pasa al título nuevo, como en el alta.
  useEffect(() => {
    if (sentTo) {
      doneTitle.current?.focus()
    }
  }, [sentTo])

  if (sentTo) {
    return (
      <>
        <header className={styles.header}>
          <h1 ref={doneTitle} tabIndex={-1} className={styles.title}>
            Revisá tu correo
          </h1>
          <p className={styles.lede}>
            Si <strong>{sentTo}</strong> tiene una cuenta, te enviamos un enlace para elegir una
            contraseña nueva. Vence en 24 horas y sirve una sola vez.
          </p>
          <p className={styles.lede}>Si en unos minutos no te llegó, revisá el correo no deseado.</p>
        </header>
        <Button variant="secondary" block onClick={() => setSentTo(null)}>
          Pedir otro enlace
        </Button>
        <p className={styles.footer}>
          ¿Te acordaste?{' '}
          <Link to="/login" className={styles.link}>
            Iniciá sesión
          </Link>
        </p>
      </>
    )
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const invalid = validateEmail(email)
    setFieldError(invalid)
    setFormError(null)
    if (invalid) {
      emailInput.current?.focus()
      return
    }

    setSending(true)
    try {
      const trimmedEmail = email.trim()
      await forgotPassword(trimmedEmail)
      setSentTo(trimmedEmail)
    } catch (error) {
      setFormError(errorMessage(error, 'Revisá el correo e intentá de nuevo.'))
    } finally {
      setSending(false)
    }
  }

  return (
    <>
      <header className={styles.header}>
        <h1 className={styles.title}>Recuperar la contraseña</h1>
        <p className={styles.lede}>Te mandamos un enlace al correo de tu cuenta para que elijas una nueva.</p>
      </header>

      <form className={styles.form} onSubmit={submit} noValidate>
        {formError && <Notice tone="danger">{formError}</Notice>}
        <TextField
          ref={emailInput}
          label="Correo de tu cuenta"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={fieldError}
        />
        <Button type="submit" block disabled={sending}>
          {sending ? 'Enviando…' : 'Enviarme el enlace'}
        </Button>
      </form>

      <p className={styles.footer}>
        ¿Te acordaste?{' '}
        <Link to="/login" className={styles.link}>
          Iniciá sesión
        </Link>
      </p>
    </>
  )
}

/** El correo que el login pasa en el estado de la navegación, si viene. */
function emailFrom(state: unknown): string {
  if (typeof state === 'object' && state !== null && 'email' in state && typeof state.email === 'string') {
    return state.email
  }
  return ''
}
