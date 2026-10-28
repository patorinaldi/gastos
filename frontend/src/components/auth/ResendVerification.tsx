import { useRef, useState, type FormEvent } from 'react'
import { resendVerification } from '../../api/auth.ts'
import { errorMessage } from '../../api/client.ts'
import { validateEmail } from '../../validation.ts'
import { Button } from '../ui/Button.tsx'
import { Notice } from '../ui/Notice.tsx'
import { TextField } from '../ui/TextField.tsx'
import styles from './ResendVerification.module.css'

interface ResendVerificationProps {
  /**
   * El correo, si la pantalla ya lo tiene: el del alta o el que se escribió en el login. Sin él, se
   * pide en un campo, como al abrir un enlace vencido.
   */
  email?: string
}

/**
 * Pide un enlace de confirmación nuevo (HU-02, enlace vencido).
 *
 * El servidor responde lo mismo exista o no la cuenta y esté o no verificada, para no revelar
 * quién tiene cuenta. Por eso el mensaje de éxito es condicional: "si hay una cuenta sin confirmar".
 */
export function ResendVerification({ email }: ResendVerificationProps) {
  const [typedEmail, setTypedEmail] = useState('')
  const [fieldError, setFieldError] = useState<string | undefined>()
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const emailInput = useRef<HTMLInputElement>(null)

  const send = async (target: string) => {
    setSending(true)
    setError(null)
    setSentTo(null)
    try {
      await resendVerification(target)
      setSentTo(target)
    } catch (failure) {
      setError(errorMessage(failure, 'Revisá el correo e intentá de nuevo.'))
    } finally {
      setSending(false)
    }
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const invalid = validateEmail(typedEmail)
    setFieldError(invalid)
    if (invalid) {
      emailInput.current?.focus()
      return
    }
    void send(typedEmail.trim())
  }

  // role="status" para que el lector de pantalla anuncie el resultado sin mover el foco.
  const result = (
    <div role="status">
      {sentTo && (
        <Notice>
          Si {sentTo} tiene una cuenta sin confirmar, te mandamos un enlace nuevo. Revisá también el
          correo no deseado.
        </Notice>
      )}
      {error && <Notice tone="danger">{error}</Notice>}
    </div>
  )

  if (email !== undefined) {
    return (
      <div className={styles.resend}>
        {result}
        <Button variant="secondary" block disabled={sending || email === ''} onClick={() => void send(email)}>
          {sending ? 'Enviando…' : 'Reenviar el enlace de confirmación'}
        </Button>
      </div>
    )
  }

  return (
    <form className={styles.resend} onSubmit={submit} noValidate>
      {result}
      <TextField
        ref={emailInput}
        label="Correo de tu cuenta"
        type="email"
        autoComplete="email"
        value={typedEmail}
        onChange={(event) => setTypedEmail(event.target.value)}
        error={fieldError}
      />
      <Button type="submit" block disabled={sending}>
        {sending ? 'Enviando…' : 'Enviarme un enlace nuevo'}
      </Button>
    </form>
  )
}
