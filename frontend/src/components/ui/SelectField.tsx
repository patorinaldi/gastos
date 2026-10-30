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
  /** `compact` es la variante de los filtros: más baja y con letra más chica. */
  size?: 'normal' | 'compact'
}

/**
 * Lista desplegable con su etiqueta, con el mismo aspecto que TextField. Es el `select` del
 * navegador: en el teléfono abre el selector nativo, que es el más cómodo para elegir con el dedo.
 */
export function SelectField({ label, options, hint, size = 'normal', id, className, ...props }: SelectFieldProps) {
  const generatedId = useId()
  const selectId = id ?? generatedId
  const hintId = `${selectId}-hint`

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
          aria-describedby={hint ? hintId : undefined}
        >
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>
      {hint && (
        <p id={hintId} className={styles.hint}>
          {hint}
        </p>
      )}
    </div>
  )
}
