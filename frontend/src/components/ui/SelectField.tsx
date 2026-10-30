import { useId, type Ref, type SelectHTMLAttributes } from 'react'
import styles from './SelectField.module.css'

export interface SelectOption {
  value: string
  label: string
}

interface SelectFieldProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'children' | 'size'> {
  ref?: Ref<HTMLSelectElement>
  label: string
  options: SelectOption[]
  hint?: string
  /** Error del campo, con la acción correctiva (RNF-17). Mientras está, reemplaza a la ayuda. */
  error?: string
  /** `compact` es la variante de los filtros: más baja y con letra más chica. */
  size?: 'normal' | 'compact'
}

/**
 * Lista desplegable con su etiqueta, con el mismo aspecto que TextField. Es el `select` del
 * navegador: en el teléfono abre el selector nativo, que es el más cómodo para elegir con el dedo.
 */
export function SelectField({ label, options, hint, error, size = 'normal', id, className, ...props }: SelectFieldProps) {
  const generatedId = useId()
  const selectId = id ?? generatedId
  const messageId = `${selectId}-message`
  const message = error || hint

  return (
    <div className={[styles.field, className].filter(Boolean).join(' ')}>
      <label htmlFor={selectId} className={styles.label}>
        {label}
      </label>
      <div className={styles.control}>
        <select
          {...props}
          id={selectId}
          className={size === 'compact' ? `${styles.select} ${styles.compact}` : styles.select}
          aria-invalid={error ? true : undefined}
          aria-describedby={message ? messageId : undefined}
        >
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
      {message && (
        <p id={messageId} className={error ? styles.error : styles.hint}>
          {message}
        </p>
      )}
    </div>
  )
}
