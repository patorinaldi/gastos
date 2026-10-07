import { useId, type InputHTMLAttributes } from 'react'
import styles from './TextField.module.css'

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  /** Ayuda que se muestra mientras no hay error. */
  hint?: string
  /**
   * Error del campo, en español y con la acción correctiva (RNF-17): "Ingresá un correo válido",
   * no "Formato inválido".
   */
  error?: string
}

/**
 * Campo de texto con su etiqueta. La etiqueta, la ayuda y el error quedan asociados al input, así
 * un lector de pantalla los anuncia al enfocarlo.
 */
export function TextField({ label, hint, error, id, className, ...props }: TextFieldProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const messageId = `${inputId}-message`
  const message = error ?? hint

  return (
    <div className={[styles.field, className].filter(Boolean).join(' ')}>
      <label htmlFor={inputId} className={styles.label}>
        {label}
      </label>
      <input
        id={inputId}
        className={styles.input}
        aria-invalid={error ? true : undefined}
        aria-describedby={message ? messageId : undefined}
        {...props}
      />
      {message && (
        <p id={messageId} className={error ? styles.error : styles.hint}>
          {message}
        </p>
      )}
    </div>
  )
}